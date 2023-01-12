/*
 * Copyright 2022 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calico
package html

import calico.html.codecs.AsIsCodec
import calico.html.codecs.Codec
import calico.html.defs.attrs.AriaAttrs
import calico.html.defs.attrs.HtmlAttrs
import calico.html.defs.eventProps.DocumentEventProps
import calico.html.defs.eventProps.GlobalEventProps
import calico.html.defs.eventProps.WindowEventProps
import calico.html.defs.props.HtmlProps
import calico.html.defs.tags.HtmlTags
import calico.html.keys.ClassProp
import calico.html.keys.ClassPropModifiers
import calico.html.keys.EventProp
import calico.html.keys.EventPropModifiers
import calico.html.keys.HtmlAttr
import calico.html.keys.HtmlAttrModifiers
import calico.html.keys.HtmlProp
import calico.html.keys.HtmlPropModifiers
import calico.html.Modifier
import calico.html.tags.HtmlTag
import calico.syntax.*
import calico.util.DomHotswap
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.Foldable
import cats.Hash
import cats.Monad
import cats.syntax.all.*
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import fs2.Pipe
import fs2.Stream
import org.scalajs.dom
import org.scalajs.dom.Attr
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import shapeless3.deriving.K0

object io extends Html[IO]

object Html:
  def apply[F[_]: Async]: Html[F] = new Html[F] {}

trait Html[F[_]](using F: Async[F])
    extends HtmlTags[F, HtmlTagT[F]],
      HtmlProps[F],
      GlobalEventProps[F],
      DocumentEventProps[F],
      WindowEventProps[F],
      HtmlAttrs[F],
      HtmlPropModifiers[F],
      EventPropModifiers[F],
      ClassPropModifiers[F],
      Modifiers[F],
      ChildrenModifiers[F],
      KeyedChildrenModifiers[F],
      HtmlAttrModifiers[F]:
  final val aria: Aria[F] = Aria[F]

  protected def htmlTag[E <: fs2.dom.HtmlElement[F]](tagName: String, void: Boolean) =
    HtmlTag(tagName, void)

  def cls: ClassProp[F] = ClassProp[F]

  def role: HtmlAttr[F, List[String]] = HtmlAttr("role", Codec.whitespaceSeparatedStringsCodec)

  def dataAttr(suffix: String): HtmlAttr[F, String] =
    HtmlAttr("data-" + suffix, AsIsCodec.StringAsIsCodec)

  def children: Children[F] = Children[F]

  def children[K](f: K => Resource[F, fs2.dom.Node[F]]): KeyedChildren[F, K] =
    KeyedChildren[F, K](f)

  def styleAttr: HtmlAttr[F, String] =
    HtmlAttr("style", AsIsCodec.StringAsIsCodec)

type HtmlTagT[F[_]] = [E <: fs2.dom.HtmlElement[F]] =>> HtmlTag[F, E]

object Aria:
  def apply[F[_]]: Aria[F] = new Aria[F] {}

trait Aria[F[_]] extends AriaAttrs[F]

object tags:
  final class HtmlTag[F[_], E <: fs2.dom.HtmlElement[F]] private[calico] (
      name: String,
      void: Boolean)(using F: Async[F]):

    def apply[M](modifier: M)(using M: Modifier[F, E, M]): Resource[F, E] =
      build.toResource.flatTap(M.modify(modifier, _))

    def apply[M](mkModifier: E => M)(using M: Modifier[F, E, M]): Resource[F, E] =
      build.toResource.flatTap(e => M.modify(mkModifier(e), e))

    def apply[M <: Tuple](modifiers: M)(
        using inst: K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
      inst.foldLeft(modifiers)(build.toResource) {
        [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
      }

    def apply[M <: Tuple](mkModifiers: E => M)(
        using inst: K0.ProductInstances[Modifier[F, E, _], M]): Resource[F, E] =
      build.toResource.flatTap { e =>
        inst.foldLeft(mkModifiers(e))(Resource.pure(e)) {
          [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
        }
      }

    private def build = F.delay(dom.document.createElement(name).asInstanceOf[E])

trait Modifier[F[_], E, A]:
  outer =>

  def modify(a: A, e: E): Resource[F, Unit]

  inline final def contramap[B](inline f: B => A): Modifier[F, E, B] =
    (b: B, e: E) => outer.modify(f(b), e)

object Modifier:
  def forSignal[F[_]: Async, A, B, C](setter: (A, B, C) => F[Unit])(
      signal: B => Signal[F, C]): Modifier[F, A, B] = (b, a) =>
    signal(b).getAndUpdates.flatMap { (head, tail) =>
      def set(c: C) = setter(a, b, c)
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

trait Modifiers[F[_]](using F: Async[F]):
  inline given forUnit[E]: Modifier[F, E, Unit] =
    _forUnit.asInstanceOf[Modifier[F, E, Unit]]

  private val _forUnit: Modifier[F, Any, Unit] =
    (_, _) => Resource.unit

  inline given forString[E <: fs2.dom.Node[F]]: Modifier[F, E, String] =
    _forString.asInstanceOf[Modifier[F, E, String]]

  private val _forString: Modifier[F, dom.Node, String] = (s, e) =>
    Resource.eval {
      F.delay {
        e.appendChild(dom.document.createTextNode(s))
        ()
      }
    }

  inline given forStringSignal[E <: fs2.dom.Node[F]]: Modifier[F, E, Signal[F, String]] =
    _forStringSignal.asInstanceOf[Modifier[F, E, Signal[F, String]]]

  private val _forStringSignal: Modifier[F, dom.Node, Signal[F, String]] = (s, e) =>
    s.getAndUpdates.flatMap { (head, tail) =>
      Resource
        .eval(F.delay(e.appendChild(dom.document.createTextNode(head))))
        .flatMap { n =>
          tail.foreach(t => F.delay(n.textContent = t)).compile.drain.cedeBackground
        }
        .void
    }

  inline given forStringOptionSignal[E <: fs2.dom.Node[F]]
      : Modifier[F, E, Signal[F, Option[String]]] =
    _forStringOptionSignal.asInstanceOf[Modifier[F, E, Signal[F, Option[String]]]]

  private val _forStringOptionSignal: Modifier[F, dom.Node, Signal[F, Option[String]]] =
    _forStringSignal.contramap(_.map(_.getOrElse("")))

  given forResource[E <: fs2.dom.Node[F], A](
      using M: Modifier[F, E, A]): Modifier[F, E, Resource[F, A]] =
    (a, e) => a.flatMap(M.modify(_, e))

  given forFoldable[E <: fs2.dom.Node[F], G[_]: Foldable, A](
      using M: Modifier[F, E, A]): Modifier[F, E, G[A]] =
    (ga, e) => ga.foldMapM(M.modify(_, e)).void

  inline given forNode[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Resource[F, N2]] =
    _forNode.asInstanceOf[Modifier[F, N, Resource[F, N2]]]

  private val _forNode: Modifier[F, dom.Node, Resource[F, dom.Node]] = (n2, n) =>
    n2.evalMap(n2 => F.delay(n.appendChild(n2)))

  inline given forNodeSignal[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Signal[F, Resource[F, N2]]] =
    _forNodeSignal.asInstanceOf[Modifier[F, N, Signal[F, Resource[F, N2]]]]

  private val _forNodeSignal: Modifier[F, dom.Node, Signal[F, Resource[F, dom.Node]]] =
    (n2s, n) =>
      n2s.getAndUpdates.flatMap { (head, tail) =>
        DomHotswap(head).flatMap { (hs, n2) =>
          F.delay(n.appendChild(n2)).toResource *>
            tail
              .foreach(hs.swap(_)((n2, n3) => F.delay(n.replaceChild(n3, n2))))
              .compile
              .drain
              .cedeBackground
        }.void
      }

  inline given forNodeOptionSignal[N <: fs2.dom.Node[F], N2 <: fs2.dom.Node[F]]
      : Modifier[F, N, Signal[F, Option[Resource[F, N2]]]] =
    _forNodeOptionSignal.asInstanceOf[Modifier[F, N, Signal[F, Option[Resource[F, N2]]]]]

  private val _forNodeOptionSignal
      : Modifier[F, dom.Node, Signal[F, Option[Resource[F, dom.Node]]]] = (n2s, n) =>
    Resource.eval(F.delay(Resource.pure[F, dom.Node](dom.document.createComment("")))).flatMap {
      sentinel => _forNodeSignal.modify(n2s.map(_.getOrElse(sentinel)), n)
    }

object keys:
  sealed class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
    import HtmlAttr.*

    inline def :=(v: V): ConstantModifier[V] =
      ConstantModifier(key, codec, v)

    inline def <--(vs: Signal[F, V]): SignalModifier[F, V] =
      SignalModifier(key, codec, vs)

    inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V] =
      OptionSignalModifier(key, codec, vs)

  object HtmlAttr:
    final class ConstantModifier[V](
        val key: String,
        val codec: Codec[V, String],
        val value: V
    )

    final class SignalModifier[F[_], V](
        val key: String,
        val codec: Codec[V, String],
        val values: Signal[F, V]
    )

    final class OptionSignalModifier[F[_], V](
        val key: String,
        val codec: Codec[V, String],
        val values: Signal[F, Option[V]]
    )

  trait HtmlAttrModifiers[F[_]](using F: Async[F]):
    import HtmlAttr.*

    inline given forConstantHtmlAttr[E <: fs2.dom.Element[F], V]
        : Modifier[F, E, ConstantModifier[V]] =
      _forConstantHtmlAttr.asInstanceOf[Modifier[F, E, ConstantModifier[V]]]

    private val _forConstantHtmlAttr: Modifier[F, dom.Element, ConstantModifier[Any]] =
      (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.codec.encode(m.value))))

    inline given forSignalHtmlAttr[E <: fs2.dom.Element[F], V]
        : Modifier[F, E, SignalModifier[F, V]] =
      _forSignalHtmlAttr.asInstanceOf[Modifier[F, E, SignalModifier[F, V]]]

    private val _forSignalHtmlAttr: Modifier[F, dom.Element, SignalModifier[F, Any]] =
      Modifier.forSignal[F, dom.Element, SignalModifier[F, Any], Any]((e, m, v) =>
        F.delay(e.setAttribute(m.key, m.codec.encode(v))))(_.values)

    inline given forOptionSignalHtmlAttr[E <: fs2.dom.Element[F], V]
        : Modifier[F, E, OptionSignalModifier[F, V]] =
      _forOptionSignalHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalModifier[F, V]]]

    private val _forOptionSignalHtmlAttr
        : Modifier[F, dom.Element, OptionSignalModifier[F, Any]] =
      Modifier.forSignal[F, dom.Element, OptionSignalModifier[F, Any], Option[Any]]((e, m, v) =>
        F.delay(v.fold(e.removeAttribute(m.key))(v =>
          e.setAttribute(m.key, m.codec.encode(v)))))(_.values)

  final class AriaAttr[F[_], V] private[calico] (suffix: String, codec: Codec[V, String])
      extends HtmlAttr[F, V]("aria-" + suffix, codec)

  sealed class HtmlProp[F[_], V, J] private[calico] (name: String, codec: Codec[V, J]):
    import HtmlProp.*

    inline def :=(v: V): ConstantModifier[V, J] =
      ConstantModifier(name, codec, v)

    inline def <--(vs: Signal[F, V]): SignalModifier[F, V, J] =
      SignalModifier(name, codec, vs)

    inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V, J] =
      OptionSignalModifier(name, codec, vs)

  object HtmlProp:
    final class ConstantModifier[V, J](
        val name: String,
        val codec: Codec[V, J],
        val value: V
    )

    final class SignalModifier[F[_], V, J](
        val name: String,
        val codec: Codec[V, J],
        val values: Signal[F, V]
    )

    final class OptionSignalModifier[F[_], V, J](
        val name: String,
        val codec: Codec[V, J],
        val values: Signal[F, Option[V]]
    )

  trait HtmlPropModifiers[F[_]](using F: Async[F]):
    import HtmlProp.*

    private[calico] inline def setHtmlProp[N, V, J](
        node: N,
        value: V,
        name: String,
        codec: Codec[V, J]) =
      F.delay(node.asInstanceOf[js.Dictionary[J]](name) = codec.encode(value))

    inline given forConstantHtmlProp[N, V, J]: Modifier[F, N, ConstantModifier[V, J]] =
      _forConstantHtmlProp.asInstanceOf[Modifier[F, N, ConstantModifier[V, J]]]

    private val _forConstantHtmlProp: Modifier[F, Any, ConstantModifier[Any, Any]] =
      (m, n) => Resource.eval(setHtmlProp(n, m.value, m.name, m.codec))

    inline given forSignalHtmlProp[N, V, J]: Modifier[F, N, SignalModifier[F, V, J]] =
      _forSignalHtmlProp.asInstanceOf[Modifier[F, N, SignalModifier[F, V, J]]]

    private val _forSignalHtmlProp: Modifier[F, Any, SignalModifier[F, Any, Any]] =
      Modifier.forSignal[F, Any, SignalModifier[F, Any, Any], Any]((any, m, v) =>
        setHtmlProp(any, v, m.name, m.codec))(_.values)

    inline given forOptionSignalHtmlProp[N, V, J]
        : Modifier[F, N, OptionSignalModifier[F, V, J]] =
      _forOptionSignalHtmlProp.asInstanceOf[Modifier[F, N, OptionSignalModifier[F, V, J]]]

    private val _forOptionSignalHtmlProp: Modifier[F, Any, OptionSignalModifier[F, Any, Any]] =
      Modifier.forSignal[F, Any, OptionSignalModifier[F, Any, Any], Option[Any]](
        (any, osm, oany) =>
          F.delay {
            val dict = any.asInstanceOf[js.Dictionary[Any]]
            oany.fold(dict -= osm.name)(v => dict(osm.name) = osm.codec.encode(v))
            ()
          })(_.values)

  final class EventProp[F[_], E] private[calico] (key: String):
    import EventProp.*
    inline def -->(sink: Pipe[F, E, Nothing]): PipeModifier[F, E] = PipeModifier(key, sink)

  object EventProp:
    final class PipeModifier[F[_], E](val key: String, val sink: Pipe[F, E, Nothing])

  trait EventPropModifiers[F[_]](using F: Async[F]):
    import EventProp.*
    inline given forPipeEventProp[T <: fs2.dom.Node[F], E]: Modifier[F, T, PipeModifier[F, E]] =
      _forPipeEventProp.asInstanceOf[Modifier[F, T, PipeModifier[F, E]]]
    private val _forPipeEventProp: Modifier[F, dom.EventTarget, PipeModifier[F, Any]] =
      (m, t) => fs2.dom.events(t, m.key).through(m.sink).compile.drain.cedeBackground.void

  final class ClassProp[F[_]] private[calico]
      extends HtmlProp[F, List[String], String](
        "className",
        Codec.whitespaceSeparatedStringsCodec
      ):
    import ClassProp.*

    inline def :=(cls: String): SingleConstantModifier =
      SingleConstantModifier(cls)

  object ClassProp:
    final class SingleConstantModifier(val cls: String)

  trait ClassPropModifiers[F[_]](using F: Async[F]):
    import ClassProp.*
    inline given forConstantClassProp[N]: Modifier[F, N, SingleConstantModifier] =
      _forConstantClassProp.asInstanceOf[Modifier[F, N, SingleConstantModifier]]
    private val _forConstantClassProp: Modifier[F, Any, SingleConstantModifier] =
      (m, n) =>
        Resource.eval(F.delay(n.asInstanceOf[js.Dictionary[String]]("className") = m.cls))

final class Children[F[_]] private[calico]:
  import Children.*

  inline def <--(
      cs: Signal[F, List[Resource[F, fs2.dom.Node[F]]]]): ResourceListSignalModifier[F] =
    ResourceListSignalModifier(cs)

  inline def <--(
      cs: Signal[F, Resource[F, List[fs2.dom.Node[F]]]]): ListResourceSignalModifier[F] =
    ListResourceSignalModifier(cs)

object Children:
  final class ResourceListSignalModifier[F[_]](
      val children: Signal[F, List[Resource[F, fs2.dom.Node[F]]]])
  final class ListResourceSignalModifier[F[_]](
      val children: Signal[F, Resource[F, List[fs2.dom.Node[F]]]])

trait ChildrenModifiers[F[_]](using F: Async[F]):
  import Children.*

  inline given forListResourceSignalChildren[N <: fs2.dom.Node[F]]
      : Modifier[F, N, ListResourceSignalModifier[F]] =
    _forListResourceSignalChildren.asInstanceOf[Modifier[F, N, ListResourceSignalModifier[F]]]

  private val _forListResourceSignalChildren
      : Modifier[F, dom.Node, ListResourceSignalModifier[F]] = (m, n) =>
    impl(n, m.children.asInstanceOf[Signal[F, Resource[F, List[dom.Node]]]])

  inline given forResourceListSignalChildren[N <: fs2.dom.Node[F]]
      : Modifier[F, N, ResourceListSignalModifier[F]] =
    _forResourceListSignalChildren.asInstanceOf[Modifier[F, N, ResourceListSignalModifier[F]]]

  private val _forResourceListSignalChildren
      : Modifier[F, dom.Node, ResourceListSignalModifier[F]] = (m, n) =>
    impl(
      n,
      m.children.map { children =>
        def go(
            in: List[Resource[F, dom.Node]],
            out: ListBuffer[dom.Node]
        ): Resource[F, List[dom.Node]] =
          if in.isEmpty then Resource.pure(out.toList)
          else
            in.head.flatMap { c =>
              out += c
              go(in.tail, out)
            }

        go(children.asInstanceOf[List[Resource[F, dom.Node]]], new ListBuffer)
      }
    )

  private def impl(n: dom.Node, children: Signal[F, Resource[F, List[dom.Node]]]) =
    for
      (head, tail) <- children.getAndUpdates
      (hs, generation0) <- DomHotswap(head)
      sentinel <- Resource.eval {
        F.delay {
          generation0.foreach(n.appendChild(_))
          n.appendChild(dom.document.createComment(""))
        }
      }
      _ <- tail
        .foreach { children =>
          hs.swap(children) { (prev, next) =>
            F.delay {
              prev.foreach(n.removeChild)
              next.foreach(n.insertBefore(_, sentinel))
            }
          }
        }
        .compile
        .drain
        .cedeBackground
    yield ()

final class KeyedChildren[F[_], K] private[calico] (f: K => Resource[F, fs2.dom.Node[F]]):
  import KeyedChildren.*
  inline def <--(ks: Signal[F, List[K]]): ListSignalModifier[F, K] = ListSignalModifier(f, ks)

object KeyedChildren:
  final class ListSignalModifier[F[_], K](
      val build: K => Resource[F, fs2.dom.Node[F]],
      val keys: Signal[F, List[K]]
  )

trait KeyedChildrenModifiers[F[_]](using F: Async[F]):
  import KeyedChildren.*

  private def traverse_[A, U](it: Iterable[A])(f: A => F[U]): F[Unit] =
    it.foldLeft(F.unit)(_ <* f(_))

  given forListSignalKeyedChildren[N <: fs2.dom.Node[F], K: Hash]
      : Modifier[F, N, ListSignalModifier[F, K]] = (m, _n) =>
    val n = _n.asInstanceOf[dom.Node]
    inline def build(k: K) = m.build(k).asInstanceOf[Resource[F, dom.Node]]
    for
      (head, tail) <- m.keys.getAndUpdates
      active <- Resource.makeFull[F, Ref[F, mutable.Map[K, (dom.Node, F[Unit])]]] { poll =>
        def go(keys: List[K], active: mutable.Map[K, (dom.Node, F[Unit])]): F[Unit] =
          if keys.isEmpty then F.unit
          else
            val k = keys.head
            poll(build(k).allocated).flatMap { v =>
              active += k -> v
              F.delay(n.appendChild(v._1)) *> go(keys.tail, active)
            }

        F.delay(mutable.Map.empty[K, (dom.Node, F[Unit])])
          .flatTap(active => go(head, active).onCancel(traverse_(active.values)(_._2)))
          .flatMap(F.ref(_))
      }(
        _.get.flatMap(ns => traverse_(ns.values)(_._2)).evalOn(unsafe.MacrotaskExecutor)
      )
      sentinel <- Resource.eval(F.delay(n.appendChild(dom.document.createComment(""))))
      _ <- tail
        .dropWhile(_ === head)
        .changes
        .foreach { keys =>
          F.uncancelable { poll =>
            active.get.flatMap { currentNodes =>
              F.delay {
                val nextNodes = mutable.Map[K, (dom.Node, F[Unit])]()
                val newNodes = new js.Array[K]
                keys.foreach { k =>
                  currentNodes.remove(k) match
                    case Some(v) => nextNodes += (k -> v)
                    case None => newNodes += k
                }

                val releaseOldNodes = traverse_(currentNodes.values)(_._2)

                val acquireNewNodes = traverse_(newNodes) { k =>
                  poll(build(k).allocated).flatMap(x => F.delay(nextNodes += k -> x))
                }

                val renderNextNodes = F.delay {
                  keys.foreach(k => n.insertBefore(nextNodes(k)._1, sentinel))
                  currentNodes.values.foreach((c, _) => n.removeChild(c))
                }

                (active.set(nextNodes) *> acquireNewNodes *> renderNextNodes).guarantee(
                  releaseOldNodes.evalOn(unsafe.MacrotaskExecutor)
                )
              }.flatten
            }
          }
        }
        .compile
        .drain
        .cedeBackground
    yield ()

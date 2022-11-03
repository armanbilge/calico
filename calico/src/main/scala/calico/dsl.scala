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
package dsl

import calico.syntax.*
import calico.util.DomHotswap
import cats.Foldable
import cats.Hash
import cats.Monad
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.raquo.domtypes.generic.builders.EventPropBuilder
import com.raquo.domtypes.generic.builders.HtmlAttrBuilder
import com.raquo.domtypes.generic.builders.HtmlTagBuilder
import com.raquo.domtypes.generic.builders.PropBuilder
import com.raquo.domtypes.generic.builders.ReflectedHtmlAttrBuilder
import com.raquo.domtypes.generic.codecs.Codec
import com.raquo.domtypes.generic.defs.attrs.*
import com.raquo.domtypes.generic.defs.complex.*
import com.raquo.domtypes.generic.defs.props.*
import com.raquo.domtypes.generic.defs.reflectedAttrs.*
import com.raquo.domtypes.jsdom.defs.eventProps.*
import com.raquo.domtypes.jsdom.defs.tags.*
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.collection.mutable
import scala.scalajs.js

object io extends Html[IO]

object Html:
  def apply[F[_]: Async]: Html[F] = new Html[F] {}

trait Html[F[_]]
    extends HtmlBuilders[F],
      DocumentTags[HtmlTagT[F]],
      GroupingTags[HtmlTagT[F]],
      TextTags[HtmlTagT[F]],
      FormTags[HtmlTagT[F]],
      SectionTags[HtmlTagT[F]],
      EmbedTags[HtmlTagT[F]],
      TableTags[HtmlTagT[F]],
      MiscTags[HtmlTagT[F]],
      HtmlAttrs[HtmlAttr[F, _]],
      ReflectedHtmlAttrs[Prop[F, _, _]],
      Props[Prop[F, _, _]],
      ClipboardEventProps[EventProp[F, _]],
      ErrorEventProps[EventProp[F, _]],
      FormEventProps[EventProp[F, _]],
      KeyboardEventProps[EventProp[F, _]],
      MediaEventProps[EventProp[F, _]],
      MiscellaneousEventProps[EventProp[F, _]],
      MouseEventProps[EventProp[F, _]],
      PointerEventProps[EventProp[F, _]]

trait HtmlBuilders[F[_]](using F: Async[F])
    extends HtmlTagBuilder[HtmlTagT[F], dom.HTMLElement],
      HtmlAttrBuilder[HtmlAttr[F, _]],
      ReflectedHtmlAttrBuilder[Prop[F, _, _]],
      PropBuilder[Prop[F, _, _]],
      EventPropBuilder[EventProp[F, _], dom.Event]:

  protected def htmlTag[E <: dom.HTMLElement](tagName: String, void: Boolean) =
    HtmlTag(tagName, void)

  protected def htmlAttr[V](key: String, codec: Codec[V, String]) =
    HtmlAttr(key, codec)

  protected def reflectedAttr[V, J](
      attrKey: String,
      propKey: String,
      attrCodec: Codec[V, String],
      propCodec: Codec[V, J]) =
    Prop(propKey, propCodec)

  protected def prop[V, J](name: String, codec: Codec[V, J]) =
    Prop(name, codec)

  def eventProp[V <: dom.Event](key: String): EventProp[F, V] =
    EventProp(key)

  def cls: ClassProp[F] = ClassProp[F]

  def children: Children[F] = Children[F]

  def children[K](f: K => Resource[F, dom.Node]): KeyedChildren[F, K] =
    KeyedChildren[F, K](f)

type HtmlTagT[F[_]] = [E <: dom.HTMLElement] =>> HtmlTag[F, E]

final class HtmlTag[F[_], E <: dom.HTMLElement] private[calico] (name: String, void: Boolean)(
    using F: Async[F]):

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

trait Modifiers[F[_]](using F: Async[F]):
  given forUnit[E]: Modifier[F, E, Unit] =
    (unit, e) => Resource.unit

  given forString[E <: dom.Node]: Modifier[F, E, String] = (s, e) =>
    Resource.eval {
      F.delay {
        e.appendChild(dom.document.createTextNode(s))
        ()
      }
    }

  given forStringSignal[E <: dom.Node]: Modifier[F, E, Signal[F, String]] = (s, e) =>
    s.getAndUpdates.flatMap { (head, tail) =>
      Resource
        .eval {
          F.delay {
            val n = dom.document.createTextNode(head)
            e.appendChild(n)
            n
          }
        }
        .flatMap { n =>
          tail.foreach(t => F.delay(n.textContent = t)).compile.drain.cedeBackground
        }
        .void
    }

  given forStringOptionSignal[E <: dom.Node]: Modifier[F, E, Signal[F, Option[String]]] =
    forStringSignal[E].contramap(_.map(_.getOrElse("")))

  given forResource[E <: dom.Node, A](
      using M: Modifier[F, E, A]): Modifier[F, E, Resource[F, A]] =
    (a, e) => a.flatMap(M.modify(_, e))

  given forFoldable[E <: dom.Node, G[_]: Foldable, A](
      using M: Modifier[F, E, A]): Modifier[F, E, G[A]] =
    (ga, e) => ga.foldMapM(M.modify(_, e)).void

  given forNode[N <: dom.Node, N2 <: dom.Node]: Modifier[F, N, Resource[F, N2]] = (n2, n) =>
    n2.evalMap(n2 => F.delay(n.appendChild(n2)))

  given forNodeSignal[N <: dom.Node, N2 <: dom.Node]
      : Modifier[F, N, Signal[F, Resource[F, N2]]] = (n2s, n) =>
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

  given forNodeOptionSignal[N <: dom.Node, N2 <: dom.Node]
      : Modifier[F, N, Signal[F, Option[Resource[F, N2]]]] = (n2s, n) =>
    Resource.eval(F.delay(Resource.pure[F, dom.Node](dom.document.createComment("")))).flatMap {
      sentinel => forNodeSignal.modify(n2s.map(_.getOrElse(sentinel)), n)
    }

final class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
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

  given forConstant[E <: dom.Element, V]: Modifier[F, E, ConstantModifier[V]] =
    (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.codec.encode(m.value))))

  given forSignal[E <: dom.Element, V]: Modifier[F, E, SignalModifier[F, V]] = (m, e) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: V) = F.delay(e.setAttribute(m.key, m.codec.encode(v)))
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

  given forOptionSignal[E <: dom.Element, V]: Modifier[F, E, OptionSignalModifier[F, V]] =
    (m, e) =>
      m.values.getAndUpdates.flatMap { (head, tail) =>
        def set(v: Option[V]) = F.delay {
          v.fold(e.removeAttribute(m.key))(v => e.setAttribute(m.key, m.codec.encode(v)))
        }
        Resource.eval(set(head)) *>
          tail.foreach(set(_)).compile.drain.cedeBackground.void
      }

sealed class Prop[F[_], V, J] private[calico] (name: String, codec: Codec[V, J]):
  import Prop.*

  inline def :=(v: V): ConstantModifier[V, J] =
    ConstantModifier(name, codec, v)

  inline def <--(vs: Signal[F, V]): SignalModifier[F, V, J] =
    SignalModifier(name, codec, vs)

  inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, V, J] =
    OptionSignalModifier(name, codec, vs)

object Prop:
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

trait PropModifiers[F[_]](using F: Async[F]):
  import Prop.*

  private inline def setProp[N, V, J](node: N, value: V, name: String, codec: Codec[V, J]) =
    F.delay(node.asInstanceOf[js.Dictionary[J]](name) = codec.encode(value))

  given forConstant[N, V, J]: Modifier[F, N, ConstantModifier[V, J]] =
    (m, n) => Resource.eval(setProp(n, m.value, m.name, m.codec))

  given forSignal[N, V, J]: Modifier[F, N, SignalModifier[F, V, J]] = (m, n) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: V) = setProp(n, v, m.name, m.codec)
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

  given forOptionSignal[N, V, J]: Modifier[F, N, OptionSignalModifier[F, V, J]] = (m, n) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: Option[V]) = F.delay {
        val dict = n.asInstanceOf[js.Dictionary[J]]
        v.fold(dict -= m.name)(v => dict(m.name) = m.codec.encode(v))
        ()
      }
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

final class EventProp[F[_], E] private[calico] (key: String):
  def -->(sink: Pipe[F, E, Nothing]): EventProp.Modified[F, E] = EventProp.Modified(key, sink)

object EventProp:
  final class Modified[F[_], E] private[calico] (val key: String, val sink: Pipe[F, E, Nothing])

  given [F[_], E <: dom.EventTarget, V](using F: Async[F]): Modifier[F, E, Modified[F, V]] with
    def modify(prop: Modified[F, V], e: E) =
      fs2.dom.events(e, prop.key).through(prop.sink).compile.drain.background.void

final class ClassProp[F[_]] private[calico]
    extends Prop[F, List[String], String](
      "className",
      new:
        def decode(domValue: String) = domValue.split(" ").toList

        def encode(scalaValue: List[String]) =
          if scalaValue.isEmpty then ""
          else
            var acc = scalaValue.head
            var tail = scalaValue.tail
            while tail.nonEmpty do
              acc += " " + tail.head
              tail = tail.tail
            acc
    ):
  import ClassProp.*

  inline def :=(cls: String): SingleConstantModifier =
    SingleConstantModifier(cls)

object ClassProp:
  final class SingleConstantModifier(val cls: String)

trait ClassPropModifiers[F[_]](using F: Async[F]):
  import ClassProp.*
  given forConstant[N]: Modifier[F, N, SingleConstantModifier] =
    (m, n) => Resource.eval(F.delay(n.asInstanceOf[js.Dictionary[String]]("className") = m.cls))

final class Children[F[_]] private[calico]:
  def <--(cs: Signal[F, List[Resource[F, dom.Node]]])(using Monad[F]): Children.Modified[F] =
    this <-- cs.discrete

  def <--(cs: Stream[F, List[Resource[F, dom.Node]]])(using Monad[F]): Children.Modified[F] =
    this <-- cs.map(_.sequence)

  def <--(cs: Signal[F, Resource[F, List[dom.Node]]]): Children.Modified[F] =
    this <-- cs.discrete

  def <--(cs: Stream[F, Resource[F, List[dom.Node]]]): Children.Modified[F] =
    Children.Modified(cs)

object Children:
  final class Modified[F[_]] private[calico] (val cs: Stream[F, Resource[F, List[dom.Node]]])

  given [F[_], E <: dom.Element](using F: Async[F]): Modifier[F, E, Modified[F]] with
    def modify(children: Modified[F], e: E) =
      for
        hs <- DomHotswap[F, List[dom.Node]](Resource.pure(Nil))
        placeholder <- Resource.eval(
          F.delay(e.appendChild(dom.document.createComment("")))
        )
        _ <- children
          .cs
          .foreach { children =>
            // hs.swap(children) { (prev, next) =>
            //   F.delay {
            //     prev.foreach(e.removeChild)
            //     next.foreach(e.insertBefore(_, placeholder))
            //   }
            // }
            ???
          }
          .compile
          .drain
          .background
          .void
      yield ()

final class KeyedChildren[F[_], K] private[calico] (f: K => Resource[F, dom.Node]):
  def <--(ks: Signal[F, List[K]]): KeyedChildren.Modified[F, K] =
    this <-- ks.discrete

  def <--(ks: Stream[F, List[K]]): KeyedChildren.Modified[F, K] =
    KeyedChildren.Modified(f, ks)

object KeyedChildren:
  final class Modified[F[_], K] private[calico] (
      val f: K => Resource[F, dom.Node],
      val ks: Stream[F, List[K]])

  given [F[_], E <: dom.Element, K: Hash](using F: Async[F]): Modifier[F, E, Modified[F, K]]
    with
    def modify(children: Modified[F, K], e: E) =
      for
        active <- Resource.make(Ref[F].of(mutable.Map.empty[K, (dom.Node, F[Unit])]))(
          _.get.flatMap(_.values.toList.traverse_(_._2))
        )
        _ <- children
          .ks
          .foreach { ks =>
            active.get.flatMap { currentNodes =>
              F.uncancelable { poll =>
                F.delay {
                  val nextNodes = mutable.Map[K, (dom.Node, F[Unit])]()
                  val newNodes = List.newBuilder[K]
                  ks.foreach { k =>
                    currentNodes.remove(k) match
                      case Some(v) => nextNodes += (k -> v)
                      case None => newNodes += k
                  }

                  val releaseOldNodes = currentNodes.values.toList.traverse_(_._2)

                  val acquireNewNodes = newNodes.result().traverse_ { k =>
                    poll(children.f(k).allocated).flatMap(x => F.delay(nextNodes += k -> x))
                  }

                  val renderNextNodes = F.delay(e.replaceChildren(ks.map(nextNodes(_)._1)*))

                  (active.set(nextNodes) *>
                    acquireNewNodes *>
                    renderNextNodes).guarantee(releaseOldNodes.evalOn(unsafe.MacrotaskExecutor))
                }.flatten
              }
            }
          }
          .compile
          .drain
          .background
      yield ()

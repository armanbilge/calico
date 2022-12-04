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
import com.raquo.domtypes.generic.defs.tags.*
import com.raquo.domtypes.jsdom.defs.eventProps.*
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import fs2.Pipe
import fs2.Stream
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.scalajs.js

object io extends Html[IO]

object Html:
  def apply[F[_]: Async]: Html[F] = new Html[F] {}

trait Html[F[_]]
    extends HtmlBuilders[F],
      DocumentTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlHtmlElement[F],
        fs2.dom.HtmlHeadElement[F],
        fs2.dom.HtmlBaseElement[F],
        fs2.dom.HtmlLinkElement[F],
        fs2.dom.HtmlMetaElement[F],
        fs2.dom.HtmlScriptElement[F],
        fs2.dom.HtmlElement[F],
      ],
      GroupingTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlParagraphElement[F],
        fs2.dom.HtmlHrElement[F],
        fs2.dom.HtmlPreElement[F],
        fs2.dom.HtmlQuoteElement[F],
        fs2.dom.HtmlOListElement[F],
        fs2.dom.HtmlUListElement[F],
        fs2.dom.HtmlLiElement[F],
        fs2.dom.HtmlDListElement[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlDivElement[F],
      ],
      TextTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlAnchorElement[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlSpanElement[F],
        fs2.dom.HtmlBrElement[F],
        fs2.dom.HtmlModElement[F],
      ],
      FormTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlFormElement[F],
        fs2.dom.HtmlFieldSetElement[F],
        fs2.dom.HtmlLegendElement[F],
        fs2.dom.HtmlLabelElement[F],
        fs2.dom.HtmlInputElement[F],
        fs2.dom.HtmlButtonElement[F],
        fs2.dom.HtmlSelectElement[F],
        fs2.dom.HtmlDataListElement[F],
        fs2.dom.HtmlOptGroupElement[F],
        fs2.dom.HtmlOptionElement[F],
        fs2.dom.HtmlTextAreaElement[F],
      ],
      SectionTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlBodyElement[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlHeadingElement[F],
      ],
      EmbedTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlImageElement[F],
        fs2.dom.HtmlIFrameElement[F],
        fs2.dom.HtmlEmbedElement[F],
        fs2.dom.HtmlObjectElement[F],
        fs2.dom.HtmlParamElement[F],
        fs2.dom.HtmlVideoElement[F],
        fs2.dom.HtmlAudioElement[F],
        fs2.dom.HtmlSourceElement[F],
        fs2.dom.HtmlTrackElement[F],
        fs2.dom.HtmlCanvasElement[F],
        fs2.dom.HtmlMapElement[F],
        fs2.dom.HtmlAreaElement[F],
      ],
      TableTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlTableElement[F],
        fs2.dom.HtmlTableCaptionElement[F],
        fs2.dom.HtmlTableColElement[F],
        fs2.dom.HtmlTableSectionElement[F],
        fs2.dom.HtmlTableRowElement[F],
        fs2.dom.HtmlTableCellElement[F],
      ],
      MiscTags[
        HtmlTagT[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlTitleElement[F],
        fs2.dom.HtmlStyleElement[F],
        fs2.dom.HtmlElement[F],
        fs2.dom.HtmlQuoteElement[F],
        fs2.dom.HtmlProgressElement[F],
        fs2.dom.HtmlMenuElement[F],
      ],
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
    extends HtmlTagBuilder[HtmlTagT[F], fs2.dom.HtmlElement[F]],
      HtmlAttrBuilder[HtmlAttr[F, _]],
      ReflectedHtmlAttrBuilder[Prop[F, _, _]],
      PropBuilder[Prop[F, _, _]],
      EventPropBuilder[EventProp[F, _], dom.Event],
      Modifiers[F],
      HtmlAttrModifiers[F],
      PropModifiers[F],
      ClassPropModifiers[F],
      DataPropModifiers[F],
      EventPropModifiers[F],
      ChildrenModifiers[F],
      KeyedChildrenModifiers[F]:

  protected def htmlTag[E <: fs2.dom.HtmlElement[F]](tagName: String, void: Boolean) =
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

  def data(suffix: String): DataProp[F] = DataProp[F](suffix)

  def children: Children[F] = Children[F]

  def children[K](f: K => Resource[F, fs2.dom.Node[F]]): KeyedChildren[F, K] =
    KeyedChildren[F, K](f)

type HtmlTagT[F[_]] = [E <: fs2.dom.HtmlElement[F]] =>> HtmlTag[F, E]

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

  inline given forConstantHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, ConstantModifier[V]] =
    _forConstantHtmlAttr.asInstanceOf[Modifier[F, E, ConstantModifier[V]]]

  private val _forConstantHtmlAttr: Modifier[F, dom.Element, ConstantModifier[Any]] =
    (m, e) => Resource.eval(F.delay(e.setAttribute(m.key, m.codec.encode(m.value))))

  inline given forSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, SignalModifier[F, V]] =
    _forSignalHtmlAttr.asInstanceOf[Modifier[F, E, SignalModifier[F, V]]]

  private val _forSignalHtmlAttr: Modifier[F, dom.Element, SignalModifier[F, Any]] = (m, e) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: Any) = F.delay(e.setAttribute(m.key, m.codec.encode(v)))
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

  inline given forOptionSignalHtmlAttr[E <: fs2.dom.Element[F], V]
      : Modifier[F, E, OptionSignalModifier[F, V]] =
    _forOptionSignalHtmlAttr.asInstanceOf[Modifier[F, E, OptionSignalModifier[F, V]]]

  private val _forOptionSignalHtmlAttr: Modifier[F, dom.Element, OptionSignalModifier[F, Any]] =
    (m, e) =>
      m.values.getAndUpdates.flatMap { (head, tail) =>
        def set(v: Option[Any]) = F.delay {
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

  inline given forConstantProp[N, V, J]: Modifier[F, N, ConstantModifier[V, J]] =
    _forConstantProp.asInstanceOf[Modifier[F, N, ConstantModifier[V, J]]]

  private val _forConstantProp: Modifier[F, Any, ConstantModifier[Any, Any]] =
    (m, n) => Resource.eval(setProp(n, m.value, m.name, m.codec))

  inline given forSignalProp[N, V, J]: Modifier[F, N, SignalModifier[F, V, J]] =
    _forSignalProp.asInstanceOf[Modifier[F, N, SignalModifier[F, V, J]]]

  private val _forSignalProp: Modifier[F, Any, SignalModifier[F, Any, Any]] = (m, n) =>
    m.values.getAndUpdates.flatMap { (head, tail) =>
      def set(v: Any) = setProp(n, v, m.name, m.codec)
      Resource.eval(set(head)) *>
        tail.foreach(set(_)).compile.drain.cedeBackground.void
    }

  inline given forOptionSignalProp[N, V, J]: Modifier[F, N, OptionSignalModifier[F, V, J]] =
    _forOptionSignalProp.asInstanceOf[Modifier[F, N, OptionSignalModifier[F, V, J]]]

  private val _forOptionSignalProp: Modifier[F, Any, OptionSignalModifier[F, Any, Any]] =
    (m, n) =>
      m.values.getAndUpdates.flatMap { (head, tail) =>
        def set(v: Option[Any]) = F.delay {
          val dict = n.asInstanceOf[js.Dictionary[Any]]
          v.fold(dict -= m.name)(v => dict(m.name) = m.codec.encode(v))
          ()
        }
        Resource.eval(set(head)) *>
          tail.foreach(set(_)).compile.drain.cedeBackground.void
      }

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
  inline given forConstantClassProp[N]: Modifier[F, N, SingleConstantModifier] =
    _forConstantClassProp.asInstanceOf[Modifier[F, N, SingleConstantModifier]]
  private val _forConstantClassProp: Modifier[F, Any, SingleConstantModifier] =
    (m, n) => Resource.eval(F.delay(n.asInstanceOf[js.Dictionary[String]]("className") = m.cls))

final class DataProp[F[_]] private[calico] (suffix: String)
    extends Prop[F, DataProp.SingleConstantModifier, String](
      s"data-$suffix",
      new:
        def decode(domValue: String) = DataProp.SingleConstantModifier(suffix, domValue)

        def encode(scalaValue: DataProp.SingleConstantModifier) = scalaValue.value
    ):
  import DataProp.*

  inline def :=(value: String): SingleConstantModifier =
    SingleConstantModifier(suffix, value)

object DataProp:
  final class SingleConstantModifier(val suffix: String, val value: String)

trait DataPropModifiers[F[_]](using F: Async[F]):
  import DataProp.*
  inline given forConstantDataProp[N <: fs2.dom.HtmlElement[F]]
      : Modifier[F, N, SingleConstantModifier] = (m, n) =>
    Resource.eval(F.delay(n.asInstanceOf[dom.HTMLElement].dataset(m.suffix) = m.value))

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
        _.get.flatMap(ns => traverse_(ns.values)(_._2)).evalOn(unsafe.BatchingMacrotaskExecutor)
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
                  releaseOldNodes.evalOn(unsafe.BatchingMacrotaskExecutor)
                )
              }.flatten
            }
          }
        }
        .compile
        .drain
        .cedeBackground
    yield ()

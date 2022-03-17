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

import cats.effect.IO
import cats.effect.kernel.Async
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
import com.raquo.domtypes.jsdom.defs.eventProps.*
import com.raquo.domtypes.jsdom.defs.tags.*
import fs2.INothing
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Channel
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.scalajs.concurrent.QueueExecutionContext
import scala.scalajs.js

object io extends Dsl[IO]

trait Dsl[F[_]]
    extends HtmlBuilders[F],
      DocumentTags[HtmlTagT[F]],
      GroupingTags[HtmlTagT[F]],
      TextTags[HtmlTagT[F]],
      FormTags[HtmlTagT[F]],
      SectionTags[HtmlTagT[F]],
      EmbedTags[HtmlTagT[F]],
      TableTags[HtmlTagT[F]],
      MiscTags[HtmlTagT[F]],
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

type HtmlTagT[F[_]] = [E <: dom.HTMLElement] =>> HtmlTag[F, E]
final class HtmlTag[F[_], E <: dom.HTMLElement] private[calico] (name: String, void: Boolean)(
    using F: Async[F]):

  def apply[M](modifier: M)(using Modifier[F, E, M]): Resource[F, E] =
    apply(Tuple1(modifier))

  def apply[M <: Tuple](modifiers: M)(
      using inst: K0.ProductInstances[Modifier[F, E, *], M]): Resource[F, E] =
    inst.foldLeft(modifiers)(build.toResource) {
      [a] => (r: Resource[F, E], m: Modifier[F, E, a], a: a) => r.flatTap(m.modify(a, _))
    }

  private def build = F.delay(dom.document.createElement(name).asInstanceOf[E])

trait Modifier[F[_], E, A]:
  def modify(a: A, e: E): Resource[F, Unit]

object Modifier:
  given [F[_], E <: dom.Element](using F: Sync[F]): Modifier[F, E, String] with
    def modify(s: String, e: E): Resource[F, Unit] = F.delay(e.innerText += s).toResource

  given [F[_], E <: dom.Element, E2 <: dom.Element](
      using F: Sync[F]): Modifier[F, E, Resource[F, E2]] with
    def modify(e2: Resource[F, E2], e: E): Resource[F, Unit] =
      e2.evalMap(e2 => F.delay(e.appendChild(e2)))

final class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String]):
  def :=(v: V): HtmlAttr.Modified[F, V] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[Rx[F, *], V]): HtmlAttr.Modified[F, V] =
    HtmlAttr.Modified(key, codec, vs)

object HtmlAttr:
  final class Modified[F[_], V] private[HtmlAttr] (
      val key: String,
      val codec: Codec[V, String],
      val values: Stream[Rx[F, *], V]
  )

  given [F[_]: Async, E <: dom.Element, V]: Modifier[F, E, Modified[F, V]] with
    def modify(attr: Modified[F, V], e: E) =
      attr
        .values
        .foreach(v => Rx(e.setAttribute(attr.key, attr.codec.encode(v))))
        .compile
        .drain
        .background
        .void
        .mapK(Rx.renderK)

final class Prop[F[_], V, J] private[calico] (name: String, codec: Codec[V, J]):
  def :=(v: V): Prop.Modified[F, V, J] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[Rx[F, *], V]): Prop.Modified[F, V, J] =
    Prop.Modified(name, codec, vs)

object Prop:
  final class Modified[F[_], V, J] private[Prop] (
      val name: String,
      val codec: Codec[V, J],
      val values: Stream[Rx[F, *], V]
  )

  given [F[_]: Async, E <: dom.Element, V, J]: Modifier[F, E, Modified[F, V, J]] with
    def modify(prop: Modified[F, V, J], e: E) =
      prop
        .values
        .foreach { v =>
          Rx {
            e.asInstanceOf[js.Dynamic]
              .updateDynamic(prop.name)(prop.codec.encode(v).asInstanceOf[js.Any])
          }
        }
        .compile
        .drain
        .background
        .void
        .mapK(Rx.renderK)

final class EventProp[F[_], E] private[calico] (key: String):
  def -->(sink: Pipe[F, E, INothing]): EventProp.Modified[F, E] = ???

object EventProp:
  final class Modified[F[_], E] private[calico] (
      val key: String,
      val sink: Pipe[F, E, INothing])

  given [F[_], E <: dom.Element, V](using F: Async[F]): Modifier[F, E, Modified[F, V]] with
    def modify(prop: Modified[F, V], e: E) = for
      ch <- Resource.make(Channel.unbounded[F, V])(_.close.void)
      d <- Dispatcher[F].evalOn(QueueExecutionContext.promises)
      _ <- Resource.make {
        F.delay(new dom.AbortController).flatTap { c =>
          F.delay {
            e.addEventListener(
              prop.key,
              e => d.unsafeRunAndForget(ch.send(e.asInstanceOf[V])),
              js.Dynamic.literal(signal = c.signal).asInstanceOf[dom.EventListenerOptions]
            )
          }
        }
      } { c => F.delay(c.abort()) }
      _ <- ch.stream.through(prop.sink).compile.drain.background
    yield ()

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

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.raquo.domtypes.generic.builders.HtmlAttrBuilder
import com.raquo.domtypes.generic.builders.HtmlTagBuilder
import com.raquo.domtypes.generic.builders.PropBuilder
import com.raquo.domtypes.generic.builders.ReflectedHtmlAttrBuilder
import com.raquo.domtypes.generic.codecs.Codec
import com.raquo.domtypes.jsdom.defs.tags.*
import fs2.Stream
import org.scalajs.dom
import shapeless3.deriving.K0

import scala.scalajs.js

object dsl:
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
        MiscTags[HtmlTagT[F]]

trait HtmlBuilders[F[_]](using F: Async[F])
    extends HtmlTagBuilder[HtmlTagT[F], dom.HTMLElement],
      HtmlAttrBuilder[HtmlAttr[F, _]],
      ReflectedHtmlAttrBuilder[Prop[F, _, _]],
      PropBuilder[Prop[F, _, _]]:

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

  def children: HtmlChildren[F] = HtmlChildren[F]

type HtmlTagT[F[_]] = [E <: dom.HTMLElement] =>> HtmlTag[F, E]
final class HtmlTag[F[_], E <: dom.HTMLElement] private[calico] (name: String, void: Boolean)(
    using F: Async[F]):
  // def apply[EE >: E](modifiers: Modifier[F, EE]*): Resource[F, E] =
  //   build.toResource.flatTap { e => modifiers.traverse_(_.modify(e)) }

  // def apply(text: Stream[Rx[F, *], String]): Resource[F, E] =
  //   build.toResource.flatTap { e =>
  //     text.foreach(t => Rx(e.innerText = t)).compile.drain.background.mapK(Rx.renderK)
  //   }

  def apply[M <: Tuple](mods: M)(
      using inst: K0.ProductInstances[Mod[F, E, *], M]): Resource[F, E] =
    inst.foldLeft(mods)(build.toResource) {
      [a] => (r: Resource[F, E], m: Mod[F, E, a], a: a) => r.flatTap(m.modify(a, _))
    }

  private def build = F.delay(dom.document.createElement(name).asInstanceOf[E])

trait Mod[F[_], E, A]:
  def modify(a: A, e: E): Resource[F, Unit]

object Mod:
  given [F[_], E <: dom.HTMLElement](using F: Sync[F]): Mod[F, E, String] with
    def modify(s: String, e: E): Resource[F, Unit] = F.delay(e.innerText = s).toResource

sealed trait Modifier[F[_], E]:
  def modify(e: E): Resource[F, Unit]

final class HtmlChildren[F[_]] private[calico] (using F: Async[F]):
  def :=(children: List[Resource[F, dom.HTMLElement]]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        children.sequence.flatMap(_.traverse_(c => F.delay(e.appendChild(c)).toResource))

final class HtmlAttr[F[_]: Async, V] private[calico] (key: String, codec: Codec[V, String]):

  def :=(v: V): Modifier[F, dom.HTMLElement] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[Rx[F, *], V]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        vs.foreach { v => Rx(e.setAttribute(key, codec.encode(v))) }
          .compile
          .drain
          .background
          .void
          .mapK(Rx.renderK)

final class Prop[F[_]: Async, V, J] private[calico] (name: String, codec: Codec[V, J]):

  def :=(v: V): Modifier[F, dom.HTMLElement] =
    this <-- Stream.emit(v)

  def <--(vs: Stream[Rx[F, *], V]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        vs.foreach { v =>
          Rx {
            e.asInstanceOf[js.Dynamic].updateDynamic(name)(codec.encode(v).asInstanceOf[js.Any])
          }
        }.compile
          .drain
          .background
          .void
          .mapK(Rx.renderK)

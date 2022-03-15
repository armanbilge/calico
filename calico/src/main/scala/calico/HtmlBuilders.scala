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
import org.scalajs.dom

import scala.scalajs.js

object dsl:
  object io extends Dsl[IO]

  trait Dsl[F[_]] extends HtmlBuilders[F],
    DocumentTags[HtmlTag[F, _]],
    GroupingTags[HtmlTag[F, _]],
    TextTags[HtmlTag[F, _]]

trait HtmlBuilders[F[_]](using F: Sync[F])
    extends HtmlTagBuilder[HtmlTag[F, _], dom.HTMLElement],
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

final class HtmlTag[F[_], E] private[calico] (name: String, void: Boolean)(using F: Sync[F]):
  def apply(modifiers: Modifier[F, E]*): Resource[F, E] =
    F.delay(dom.document.createElement(name).asInstanceOf[E]).toResource.flatTap { e =>
      modifiers.traverse_(_.modify(e))
    }

sealed trait Modifier[F[_], E]:
  def modify(e: E): Resource[F, Unit]

final class HtmlChildren[F[_]] private[calico] (using F: Sync[F]):
  def :=(children: List[Resource[F, dom.HTMLElement]]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        children.sequence.flatMap(_.traverse_(c => F.delay(e.appendChild(c)).toResource))

final class HtmlAttr[F[_], V] private[calico] (key: String, codec: Codec[V, String])(
    using F: Sync[F]):

  def :=(value: V): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) = set(e, value).toResource

  def <--(rx: Rx[F, V]): Modifier[F, dom.HTMLElement] =
    this <-- Resource.pure(rx)

  def <--(rx: Resource[F, Rx[F, V]]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        rx.flatMap { rx => rx.foreach(set(e, _)) }

  private def set[G[_]](e: dom.HTMLElement, v: V)(using G: Sync[G]) =
    G.delay(e.setAttribute(key, codec.encode(v)))

final class Prop[F[_], V, J] private[calico] (name: String, codec: Codec[V, J])(
    using F: Sync[F]):

  def :=(value: V): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) = set(e, value).toResource

  def <--(rx: Rx[F, V]): Modifier[F, dom.HTMLElement] =
    this <-- Resource.pure(rx)

  def <--(rx: Resource[F, Rx[F, V]]): Modifier[F, dom.HTMLElement] =
    new:
      def modify(e: dom.HTMLElement) =
        rx.flatMap { rx => rx.foreach(set(e, _)) }

  private def set[G[_]](e: dom.HTMLElement, v: V)(using G: Sync[G]) =
    G.delay {
      e.asInstanceOf[js.Dynamic].updateDynamic(name)(codec.encode(v).asInstanceOf[js.Any])
    }

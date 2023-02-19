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

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.dom.Dom

object io extends Html[IO]

object Html:
  def apply[F[_]: Async]: Html[F] = new Html[F]

sealed class Html[F[_]](using F: Async[F])
    extends HtmlTags[F],
      Props[F],
      GlobalEventProps[F],
      DocumentEventProps[F],
      WindowEventProps[F],
      HtmlAttrs[F],
      PropModifiers[F],
      EventPropModifiers[F],
      ClassPropModifiers[F],
      Modifiers[F],
      ChildrenModifiers[F],
      KeyedChildrenModifiers[F],
      HtmlAttrModifiers[F]:

  given Dom[F] = Dom.forAsync

  def aria: Aria[F] = Aria[F]

  def cls: ClassProp[F] = ClassProp[F]

  def rel: HtmlAttr[F, List[String]] = HtmlAttr("rel", encoders.whitespaceSeparatedStrings)

  def role: HtmlAttr[F, List[String]] = HtmlAttr("role", encoders.whitespaceSeparatedStrings)

  def dataAttr(suffix: String): HtmlAttr[F, String] =
    HtmlAttr("data-" + suffix, encoders.identity)

  def children: Children[F] = Children[F]

  def children[K](f: K => Resource[F, fs2.dom.Node[F]]): KeyedChildren[F, K] =
    KeyedChildren[F, K](f)

  def styleAttr: HtmlAttr[F, String] =
    HtmlAttr("style", encoders.identity)

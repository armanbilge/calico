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

import org.scalajs.dom
import com.raquo.domtypes.generic.builders.HtmlTagBuilder
import cats.effect.kernel.Resource
import cats.effect.IO

final class HtmlTag[F[_], E](name: String, void: Boolean):
  def apply(modifiers: Any*): Resource[F, E] = ???

class HtmlBuilders[F[_]] extends HtmlTagBuilder[HtmlTag[F, *], dom.html.Element]:
  protected def htmlTag[E <: dom.html.Element](tagName: String, void: Boolean): HtmlTag[F, E] = ???

object dsl:
  object io extends HtmlBuilders[IO]

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

import cats.effect.kernel.Async
import fs2.dom.HtmlInputElement
import org.scalajs.dom

type InputValue[T <: String] <: Any = T match
  case "file" => dom.FileList
  case "checkbox" => Boolean
  case "radio" => Boolean
  case "number" => Double
  case "range" => Double
  case String => String

trait InputReader[F[_], T <: String]:
  type Out
  def read(el: HtmlInputElement[F]): F[Out]

object InputReader:

  type Aux[F[_], T <: String, O] = InputReader[F, T] { type Out = O }

  given file[F[_]](using F: Async[F]): InputReader[F, "file"] with
    type Out = dom.FileList
    def read(el: HtmlInputElement[F]): F[dom.FileList] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].files)

  given checkbox[F[_]](using F: Async[F]): InputReader[F, "checkbox"] with
    type Out = Boolean
    def read(el: HtmlInputElement[F]): F[Boolean] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].checked)

  given radio[F[_]](using F: Async[F]): InputReader[F, "radio"] with
    type Out = Boolean
    def read(el: HtmlInputElement[F]): F[Boolean] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].checked)

  given number[F[_]](using F: Async[F]): InputReader[F, "number"] with
    type Out = Double
    def read(el: HtmlInputElement[F]): F[Double] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].valueAsNumber)

  given range[F[_]](using F: Async[F]): InputReader[F, "range"] with
    type Out = Double
    def read(el: HtmlInputElement[F]): F[Double] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].valueAsNumber)

  given text[F[_], T <: String](using F: Async[F]): InputReader[F, T] with
    type Out = String
    def read(el: HtmlInputElement[F]): F[String] =
      F.delay(el.asInstanceOf[dom.HTMLInputElement].value)

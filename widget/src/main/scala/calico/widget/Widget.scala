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
package widget

import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Stream
import org.scalajs.dom
import cats.effect.kernel.Async
import calico.dsl.Dsl
import calico.syntax.*
import fs2.Pipe
import fs2.INothing
import shapeless3.deriving.K0

trait View[F[_], A]:
  outer =>

  def of(read: Stream[Rx[F, _], A]): Resource[F, dom.HTMLElement]

  final def contramap[B](f: B => A): View[F, B] = new:
    def of(read: Stream[Rx[F, _], B]) = outer.of(read.map(f))

object View:
  given string[F[_]: Async]: View[F, String] with
    def of(read: Stream[Rx[F, _], String]) = Dsl[F].div(read)

  given int[F[_]: Async]: View[F, Int] = string.contramap(_.toString)

  given product[F[_]: Async, A](using inst: K0.ProductInstances[View[F, _], A]): View[F, A] with
    def of(read: Stream[Rx[F, _], A]) = ???

trait Edit[F[_], A]:
  def of(read: Stream[Rx[F, _], A])(write: Pipe[F, A, INothing]): Resource[F, dom.HTMLElement]

object Edit:
  given string[F[_]: Async]: Edit[F, String] with
    def of(read: Stream[Rx[F, _], String])(write: Pipe[F, String, INothing]) =
      val dsl = Dsl[F]
      import dsl.*

      input(value <-- read, onInput --> (_.mapToValue.through(write)))

  given int[F[_]: Async]: Edit[F, Int] with
    def of(read: Stream[Rx[F, _], Int])(write: Pipe[F, Int, INothing]) =
      val dsl = Dsl[F]
      import dsl.*

      input(
        typ := "number",
        value <-- read.map(_.toString),
        onInput --> (_.mapToValue.map(_.toIntOption).unNone.through(write))
      )

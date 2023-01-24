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

package calico.router

import cats.Applicative
import cats.data.Kleisli
import cats.effect.kernel.Concurrent
import cats.effect.kernel.RefSink
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.kernel.Monoid
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import org.http4s.Uri

opaque type Routes[F[_]] = Kleisli[F, Uri, Option[Route[F]]]

trait Route[F[_]]:
  def key: Unique.Token
  def build(uri: Uri): Resource[F, (RefSink[F, Uri], HtmlElement[F])]

object Routes:

  extension [F[_]](routes: Routes[F])
    def apply(uri: Uri): F[Option[Route[F]]] =
      routes.run(uri)

  given [F[_]](using F: Applicative[F]): Monoid[Routes[F]] =
    given Monoid[F[Option[Route[F]]]] with
      def empty = F.pure(None)
      def combine(x: F[Option[Route[F]]], y: F[Option[Route[F]]]) =
        (x, y).mapN(_.orElse(_))

    Kleisli.catsDataMonoidForKleisli

  def apply[F[_]](f: Uri => F[Option[Route[F]]]): Routes[F] = Kleisli(f)

  /**
   * Create a [[Routes]] for one "template". The `matcher` [[PartialFunction]] may extract some
   * state `A` from the [[Uri]] that is communicated to the [[HtmlElement]] via a [[Signal]].
   * This enables the component to be re-used when the [[Uri]] changes but the same [[Route]]
   * matches although the extracted state may differ.
   */
  def one[F[_]]: OneRouteBuilder[F] = new OneRouteBuilder

  final class OneRouteBuilder[F[_]] private[Routes]:
    def apply[A](matcher: PartialFunction[Uri, A])(
        builder: Signal[F, A] => Resource[F, HtmlElement[F]])(
        using F: Concurrent[F]): F[Routes[F]] =
      F.unique.map { token =>
        val route = new Route[F]:
          def key = token

          def build(uri: Uri): Resource[F, (RefSink[F, Uri], HtmlElement[F])] =
            Resource.eval(SignallingRef[F].of(matcher(uri))).flatMap { sigRef =>
              builder(sigRef).tupleLeft((sigRef: RefSink[F, A]).contramap(matcher(_)))
            }

        new Routes(uri => Option.when(matcher.isDefinedAt(uri))(route).pure)
      }

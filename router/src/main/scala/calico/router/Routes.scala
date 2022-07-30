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
import org.http4s.Uri.Path
import org.scalajs.dom.HTMLElement

opaque type Routes[F[_]] = Kleisli[F, Path, Option[Route[F]]]

trait Route[F[_]]:
  def key: Unique.Token
  def build(path: Path): Resource[F, (RefSink[F, Path], HTMLElement)]

object Routes:

  extension [F[_]](routes: Routes[F])
    def apply(path: Path): F[Option[Route[F]]] =
      routes.run(path)

  given [F[_]](using F: Applicative[F]): Monoid[Routes[F]] =
    given Monoid[F[Option[Route[F]]]] with
      def empty = F.pure(None)
      def combine(x: F[Option[Route[F]]], y: F[Option[Route[F]]]) =
        (x, y).mapN(_.orElse(_))

    Kleisli.catsDataMonoidForKleisli

  def apply[F[_]](f: Path => F[Option[Route[F]]]): Routes[F] = Kleisli(f)

  def one[F[_], A](matcher: PartialFunction[Path, A])(
      builder: Signal[F, A] => Resource[F, HTMLElement])(using F: Concurrent[F]): F[Routes[F]] =
    F.unique.map { token =>
      val route = new Route[F]:
        def key = token

        def build(path: Path): Resource[F, (RefSink[F, Path], HTMLElement)] =
          Resource.eval(SignallingRef[F].of(matcher(path))).flatMap { sigRef =>
            builder(sigRef).tupleLeft((sigRef: RefSink[F, A]).contramap(matcher(_)))
          }

      new Routes(path => Option.when(matcher.isDefinedAt(path))(route).pure)
    }

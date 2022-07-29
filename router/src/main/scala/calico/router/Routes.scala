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

import cats.effect.kernel.Concurrent
import cats.effect.kernel.RefSink
import cats.effect.kernel.Resource
import cats.effect.kernel.Unique
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import org.http4s.Uri.Path
import org.scalajs.dom.HTMLElement

sealed abstract class Routes[F[_]]:
  def apply(path: Path): Resource[F, Option[HTMLElement]] = ???

object Routes:
  private final class Route[F[_], A](
      token: Unique.Token,
      matcher: PartialFunction[Path, A],
      builder: Signal[F, A] => Resource[F, HTMLElement]
  )(using F: Concurrent[F]):
    def make(path: Path): Resource[F, (RefSink[F, Path], HTMLElement)] =
      Resource.eval(SignallingRef[F].of(matcher(path))).flatMap { sigRef =>
        builder(sigRef).tupleLeft((sigRef: RefSink[F, A]).contramap(matcher(_)))
      }

  private final case class Pages[F[_]](
      children: List[Routes[F]]
  ) extends Routes[F]


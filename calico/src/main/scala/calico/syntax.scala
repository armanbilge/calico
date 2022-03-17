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
package syntax

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Channel
import org.scalajs.dom

extension [F[_]](component: Resource[F, dom.HTMLElement])
  def renderInto(root: dom.Element)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }

extension [F[_]: Async, A](stream: Stream[F, A])
  def renderable: Resource[F, Stream[Rx[F, *], A]] =
    for
      ch <- Channel.synchronous[Rx[F, *], A].render.toResource
      _ <- stream
        .foreach(ch.send(_).void.render)
        .onFinalize(ch.close.void.render)
        .compile
        .drain
        .background
    yield ch.stream

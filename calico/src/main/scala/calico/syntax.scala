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
import fs2.concurrent.Topic
import org.scalajs.dom

import scala.scalajs.js

extension [F[_]](component: Resource[F, dom.HTMLElement])
  def renderInto(root: dom.Element)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }

extension [F[_], A](resource: Resource[Rx[F, _], A])
  def render(using Async[F]): Resource[F, A] = resource.mapK(Rx.renderK)

extension [F[_], A](stream: Stream[F, A])
  def renderable(using Async[F]): Resource[F, Stream[Rx[F, _], A]] =
    for
      ch <- Channel.synchronous[Rx[F, _], A].render.toResource
      _ <- stream
        .foreach(ch.send(_).void.render)
        .onFinalize(ch.close.void.render)
        .compile
        .drain
        .background
    yield ch.stream

  def renderableTopic(using Async[F]): Resource[F, Topic[Rx[F, _], A]] =
    renderable.flatMap { stream =>
      Topic[Rx[F, _], A].toResource.flatTap(_.publish(stream).compile.drain.background).render
    }

extension [F[_]](events: Stream[F, dom.Event])
  def mapToValue: Stream[F, String] =
    events.map(_.target).collect { case input: dom.HTMLInputElement => input.value }

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
import cats.effect.kernel.Concurrent
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Topic
import org.scalajs.dom

import scala.scalajs.js
import fs2.concurrent.Signal

extension [F[_]](component: Resource[F, dom.HTMLElement])
  def renderInto(root: dom.Element)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }

extension [F[_], A](resource: Resource[Rx[F, _], A])
  def render(using Async[F]): Resource[F, A] = resource.mapK(Rx.renderK)
  def translate(using MonadCancel[F, ?]): Resource[F, A] = resource.mapK(Rx.translateK)

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

  def signal(using Concurrent[F]): Resource[F, Signal[F, A]] =
    for
      sig <- DeferredSignallingRef[F, A].toResource
      _ <- stream.foreach(sig.set(_)).compile.drain.background
    yield sig

  def renderableSignal(using Async[F]): Resource[F, Signal[Rx[F, _], A]] =
    for
      sig <- DeferredSignallingRef[Rx[F, _], A].render.toResource
      _ <- stream.foreach(sig.set(_).render).compile.drain.background
    yield sig

  def renderableTopic(using Async[F]): Resource[F, Topic[Rx[F, _], A]] =
    renderable.flatMap { stream =>
      Topic[Rx[F, _], A].toResource.flatTap(_.publish(stream).compile.drain.background).render
    }

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def channel(using F: Concurrent[F]): Resource[F, Channel[F, A]] =
    for
      ch <- Channel.unbounded[F, A].toResource
      _ <- ch.stream.through(pipe).compile.drain.background
    yield ch

extension [F[_]](events: Stream[F, dom.Event])
  def mapToTargetValue: Stream[F, String] =
    events.map(_.target).collect {
      case button: dom.HTMLButtonElement => button.value
      case input: dom.HTMLInputElement => input.value
      case option: dom.HTMLOptionElement => option.value
      case select: dom.HTMLSelectElement => select.value
      case textArea: dom.HTMLTextAreaElement => textArea.value
    }

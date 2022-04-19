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

import cats.data.State
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.kernel.Eq
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Topic
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import monocle.Lens
import org.scalajs.dom

import scala.scalajs.js

extension [F[_]](component: Resource[F, dom.Node])
  def renderInto(root: dom.Node)(using F: Sync[F]): Resource[F, Unit] =
    component.flatMap { e =>
      Resource.make(F.delay(root.appendChild(e)))(_ => F.delay(root.removeChild(e))).void
    }

extension [F[_], A](sigRef: SignallingRef[F, A])
  def zoom[B <: AnyRef](lens: Lens[A, B])(using Sync[F]): SignallingRef[F, B] =
    val ref = Ref.lens[F, A, B](sigRef)(lens.get(_), a => b => lens.replace(b)(a))
    new:
      def access = ref.access
      def modify[C](f: B => (B, C)) = ref.modify(f)
      def modifyState[C](state: State[B, C]) = ref.modifyState(state)
      def tryModify[C](f: B => (B, C)) = ref.tryModify(f)
      def tryModifyState[C](state: State[B, C]) = ref.tryModifyState(state)
      def tryUpdate(f: B => B) = ref.tryUpdate(f)
      def update(f: B => B) = ref.update(f)
      def set(b: B) = ref.set(b)
      def get = ref.get
      def continuous = sigRef.map(lens.get).continuous
      def discrete = sigRef.map(lens.get).discrete.changes(Eq.fromUniversalEquals)

extension [F[_], A](stream: Stream[F, A])
  @deprecated("This is not a valid signal; use Stream#holdOptionResource instead", "0.1.1")
  def signal(using Concurrent[F]): Resource[F, Signal[F, A]] =
    for
      sig <- SignallingRef[F].of(none[A]).toResource
      _ <- stream.foreach(a => sig.set(Some(a))).compile.drain.background
    yield new:
      def continuous = sig.continuous.unNone
      def discrete = sig.discrete.unNone
      def get = discrete.head.compile.lastOrError

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def channel(using F: Concurrent[F]): Resource[F, Channel[F, A]] =
    for
      ch <- Channel.unbounded[F, A].toResource
      _ <- ch.stream.through(pipe).compile.drain.background
    yield ch

extension [F[_]](events: Stream[F, dom.Event])
  def mapToTargetValue(using F: Sync[F]): Stream[F, String] =
    events
      .map(_.target)
      .evalMap {
        case button: dom.HTMLButtonElement => F.delay(button.value.some)
        case input: dom.HTMLInputElement => F.delay(input.value.some)
        case option: dom.HTMLOptionElement => F.delay(option.value.some)
        case select: dom.HTMLSelectElement => F.delay(select.value.some)
        case textArea: dom.HTMLTextAreaElement => F.delay(textArea.value.some)
        case _ => F.pure(None)
      }
      .unNone

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

import cats.Functor
import cats.data.State
import cats.effect.kernel.Async
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Fiber
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Outcome
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.kernel.Eq
import cats.syntax.all.*
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import fs2.concurrent.Channel
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import fs2.dom.Dom
import monocle.Lens
import org.scalajs.dom

import scala.scalajs.js

extension [F[_]](component: Resource[F, fs2.dom.Node[F]])
  def renderInto(root: fs2.dom.Node[F])(using Functor[F], Dom[F]): Resource[F, Unit] =
    component.flatMap { e => Resource.make(root.appendChild(e))(_ => root.removeChild(e)) }

extension [F[_], A](fa: F[A])
  private[calico] def cedeBackground(
      using F: Async[F]): Resource[F, F[Outcome[F, Throwable, A]]] =
    F.executionContext.toResource.flatMap { ec =>
      Resource
        .make(F.deferred[Fiber[F, Throwable, A]])(_.get.flatMap(_.cancel))
        .evalTap { deferred =>
          fa.start
            .flatMap(deferred.complete(_))
            .evalOn(ec)
            .startOn(unsafe.MacrotaskExecutor)
            .start
        }
        .map(_.get.flatMap(_.join))
    }

extension [F[_], A](signal: Signal[F, A])
  private[calico] def getAndUpdates(using Concurrent[F]): Resource[F, (A, Stream[F, A])] =
    signal.discrete.pull.uncons1.flatMap {
      case Some(headTail) => Pull.output1(headTail)
      case None => Pull.done
    }.streamNoScope.compile.resource.onlyOrError

  def changes(using Eq[A]): Signal[F, A] =
    new:
      def continuous = signal.continuous
      def discrete = signal.discrete.changes
      def get = signal.get

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
      def discrete = sigRef.map(lens.get).discrete

extension [F[_], A, B](pipe: Pipe[F, A, B])
  def channel(using F: Concurrent[F]): Resource[F, Channel[F, A]] =
    for
      ch <- Channel.unbounded[F, A].toResource
      _ <- ch.stream.through(pipe).compile.drain.background
    yield ch

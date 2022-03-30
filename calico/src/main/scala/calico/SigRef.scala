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

import calico.syntax.*
import cats.Monad
import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.RefSink
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import monocle.Focus
import monocle.Lens

abstract class SigRef[F[_], A]
    extends Signal[Rx[F, _], A],
      RefSink[F, A],
      DeferredSource[Rx[F, _], A]:

  final def signalF: Signal[F, A] = asInstanceOf[Signal[F, A]]

  def zoom[B](lens: Lens[A, B]): SigRef[F, B]

object SigRef:
  def apply[F[_]: Concurrent, A](a: A): F[SigRef[F, A]] = of(Some(a))

  def apply[F[_]] = new PartialApply[F]

  final class PartialApply[F[_]] private[SigRef]:
    def of[A](a: A)(using Concurrent[F]): F[SigRef[F, A]] = SigRef.of(Some(a))

  def apply[F[_]: Concurrent, A]: F[SigRef[F, A]] = of(None)

  private def of[F[_], A](a: Option[A])(using F: Concurrent[F]): F[SigRef[F, A]] =
    SignallingRef[Rx[F, _]].of(a).translate.map { sig =>
      new:
        outer =>
        def get = OptionT(tryGet).getOrElseF(discrete.head.compile.lastOrError)
        def tryGet = sig.get
        def set(a: A) = sig.set(Some(a)).translate
        def continuous = sig.continuous.unNone
        def discrete = sig.discrete.unNone
        def zoom[B](lens: Lens[A, B]) = SigRef.lens(this, lens)
    }

  def lens[F[_]: Monad, A, B](sigRef: SigRef[F, A], lens: Lens[A, B]): SigRef[F, B] =
    new:
      def get = sigRef.get.map(lens.get)
      def tryGet = OptionT(sigRef.tryGet).map(lens.get).value
      def set(b: B) = sigRef.get.translate.flatMap(a => sigRef.set(lens.replace(b)(a)))
      def continuous = sigRef.continuous.map(lens.get)
      def discrete = sigRef.discrete.map(lens.get)
      def zoom[C](lensBC: Lens[B, C]) = SigRef.lens(sigRef, lens.andThen(lensBC))

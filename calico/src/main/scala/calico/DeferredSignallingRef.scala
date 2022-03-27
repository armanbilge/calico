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

import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.RefSink
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef

abstract class DeferredSignallingRef[F[_], A]
    extends Signal[F, A],
      RefSink[F, A],
      DeferredSource[F, A]

object DeferredSignallingRef:

  def apply[F[_]: Concurrent, A](a: A): F[DeferredSignallingRef[F, A]] = of(Some(a))

  def apply[F[_]: Concurrent, A]: F[DeferredSignallingRef[F, A]] = of(None)

  private def of[F[_], A](a: Option[A])(
      using F: Concurrent[F]): F[DeferredSignallingRef[F, A]] =
    SignallingRef.of(a).map { sig =>
      new:
        def get: F[A] = OptionT(tryGet).getOrElseF(discrete.head.compile.lastOrError)
        def tryGet: F[Option[A]] = sig.get
        def set(a: A): F[Unit] = sig.set(Some(a))
        def continuous: fs2.Stream[F, A] = sig.continuous.unNone
        def discrete: fs2.Stream[F, A] = sig.discrete.unNone
    }

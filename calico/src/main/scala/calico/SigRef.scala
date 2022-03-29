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
import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.RefSink
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef

abstract class SigRef[F[_], A]
    extends Signal[Rx[F, _], A],
      RefSink[F, A],
      DeferredSource[Rx[F, _], A]

object SigRef:
  def apply[F[_]: Concurrent, A](a: A): F[SigRef[F, A]] = of(Some(a))

  def apply[F[_]: Concurrent, A]: F[SigRef[F, A]] = of(None)

  private def of[F[_], A](a: Option[A])(using F: Concurrent[F]): F[SigRef[F, A]] =
    SignallingRef[Rx[F, _]].of(a).translate.map { sig =>
      new:
        def get: Rx[F, A] = OptionT(tryGet).getOrElseF(discrete.head.compile.lastOrError)
        def tryGet: Rx[F, Option[A]] = sig.get
        def set(a: A): F[Unit] = sig.set(Some(a)).translate
        def continuous: Stream[Rx[F, _], A] = sig.continuous.unNone
        def discrete: Stream[Rx[F, _], A] = sig.discrete.unNone
    }

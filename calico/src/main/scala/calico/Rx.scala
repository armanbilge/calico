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

import cats.~>
import cats.effect.kernel.Async
import cats.effect.kernel.GenConcurrent
import cats.effect.kernel.Sync
import cats.Monad

import scala.scalajs.concurrent.QueueExecutionContext

opaque type Rx[F[_], A] = F[A]
extension [F[_], A](rxa: Rx[F[_], A])
  def render(using F: Async[F]): F[A] = F.evalOn(rxa, QueueExecutionContext.promises)

object Rx extends RxLowPriority0:
  def apply[F[_]: Sync, A](thunk: => A): Rx[F, A] = delay(thunk)
  def delay[F[_], A](thunk: => A)(using F: Sync[F]): Rx[F, A] = F.delay(thunk)

  def renderK[F[_]: Async]: Rx[F, *] ~> F =
    new:
      def apply[A](rxa: Rx[F, A]): F[A] = render(rxa)

  given [F[_], E](using F: GenConcurrent[F, E]): GenConcurrent[Rx[F, *], E] = F
  

private sealed class RxLowPriority0:
  given [F[_]](using F: Sync[F]): Sync[Rx[F, *]] = F.asInstanceOf[Sync[Rx[F, *]]]

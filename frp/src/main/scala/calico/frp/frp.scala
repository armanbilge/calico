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
package frp

import cats.Monad
import cats.StackSafeMonad
import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal

given [F[_]: Concurrent]: Monad[Signal[F, _]] = new StackSafeMonad[Signal[F, _]]:
  def pure[A](a: A) = Signal.constant(a)

  override def map[A, B](siga: Signal[F, A])(f: A => B) =
    Signal.mapped(siga)(f)

  def flatMap[A, B](siga: Signal[F, A])(f: A => Signal[F, B]) = new:
    def get = siga.get.flatMap(f(_).get)
    def continuous = Stream.repeatEval(get)
    def discrete = siga.discrete.switchMap(f(_).discrete)
    override def getAndDiscreteUpdates(using Concurrent[F]) =
      getAndDiscreteUpdatesImpl
    private def getAndDiscreteUpdatesImpl =
      siga.getAndDiscreteUpdates.flatMap { (a, as) =>
        f(a).getAndDiscreteUpdates.map { (b, bs) =>
          (b, (Stream.emit(bs) ++ as.map(f(_).discrete)).switchMap(identity(_)))
        }
      }

  override def product[A, B](fa: Signal[F, A], fb: Signal[F, B]) =
    Signal.applicativeInstance.product(fa, fb)

  override def map2[A, B, C](fa: Signal[F, A], fb: Signal[F, B])(f: (A, B) => C) =
    Signal.applicativeInstance.map2(fa, fb)(f)

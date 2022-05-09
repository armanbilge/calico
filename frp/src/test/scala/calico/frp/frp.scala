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

import cats.StackSafeMonad
import cats.effect.kernel.Concurrent
import fs2.concurrent.Signal
import cats.syntax.all.*
import fs2.Stream

given [F[_]: Concurrent]: StackSafeMonad[Signal[F, _]] with
  def pure[A](a: A) = Signal.constant(a)

  def flatMap[A, B](siga: Signal[F, A])(f: A => Signal[F, B]) = new:
    def get = siga.get.flatMap(a => f(a).get)
    def continuous = Stream.repeatEval(get)
    def discrete = siga.discrete.switchMap(f(_).discrete)

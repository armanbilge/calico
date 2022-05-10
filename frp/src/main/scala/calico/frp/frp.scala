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

/*
 * Copyright (c) 2013 Functional Streams for Scala
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package calico
package frp

import cats.StackSafeMonad
import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import fs2.Pull
import fs2.Stream
import fs2.concurrent.Signal

given [F[_]: Concurrent]: StackSafeMonad[Signal[F, _]] with
  def pure[A](a: A) = Signal.constant(a)

  def flatMap[A, B](siga: Signal[F, A])(f: A => Signal[F, B]) = new:
    def get = siga.get.flatMap(f(_).get)
    def continuous = Stream.repeatEval(get)
    def discrete = siga.discrete.switchMap(f(_).discrete)

  override def ap[A, B](ff: Signal[F, A => B])(fa: Signal[F, A]) =
    new:
      def discrete: Stream[F, B] =
        nondeterministicZip(ff.discrete, fa.discrete).map(_(_))
      def continuous: Stream[F, B] = Stream.repeatEval(get)
      def get: F[B] = ff.get.ap(fa.get)

      private def nondeterministicZip[A0, A1](
          xs: Stream[F, A0],
          ys: Stream[F, A1]
      ): Stream[F, (A0, A1)] =
        type PullOutput = (A0, A1, Stream[F, A0], Stream[F, A1])

        val firstPull: OptionT[Pull[F, PullOutput, *], Unit] = for
          firstXAndRestOfXs <- OptionT(xs.pull.uncons1.covaryOutput[PullOutput])
          (x, restOfXs) = firstXAndRestOfXs
          firstYAndRestOfYs <- OptionT(ys.pull.uncons1.covaryOutput[PullOutput])
          (y, restOfYs) = firstYAndRestOfYs
          _ <- OptionT.liftF {
            Pull.output1[F, PullOutput]((x, y, restOfXs, restOfYs)): Pull[F, PullOutput, Unit]
          }
        yield ()

        firstPull.value.void.stream.flatMap { (x, y, restOfXs, restOfYs) =>
          restOfXs.either(restOfYs).scan((x, y)) {
            case ((_, rightElem), Left(newElem)) => (newElem, rightElem)
            case ((leftElem, _), Right(newElem)) => (leftElem, newElem)
          }
        }

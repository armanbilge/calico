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

package calico.frp

import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import munit.DisciplineSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary

import scala.concurrent.duration.FiniteDuration

class SignalSuite extends DisciplineSuite:

  class TestSignal[A](initial: A, values: List[(FiniteDuration, A)]) extends Signal[IO, A]:
    def discrete: Stream[IO, A] =
      Stream.emit(initial) ++ Stream.emits(values).evalMap(IO.sleep(_).as(_))
    def get = IO.never
    def continuous = Stream.never

  given [A: Arbitrary]: Arbitrary[Signal[IO, A]] =
    Arbitrary(
      for
        initial <- arbitrary[A]
        tail <- arbitrary[List[(FiniteDuration, A)]]
      yield TestSignal(initial, tail)
    )

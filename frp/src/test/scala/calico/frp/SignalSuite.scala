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
import cats.effect.testkit.TestControl
import cats.effect.testkit.TestInstances
import cats.kernel.Eq
import cats.laws.discipline.ApplicativeTests
import cats.laws.discipline.FunctorTests
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import munit.DisciplineSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import scala.concurrent.duration.*

class SignalSuite extends DisciplineSuite, TestInstances:

  class TestSignal[A](initial: A, values: List[(FiniteDuration, A)]) extends Signal[IO, A]:
    def discrete: Stream[IO, A] =
      Stream.emit(initial) ++ Stream.emits(values).evalMap(IO.sleep(_).as(_))
    def get = IO.never
    def continuous = Stream.never

  given [A: Arbitrary]: Arbitrary[Signal[IO, A]] =
    given Arbitrary[FiniteDuration] = Arbitrary(Gen.posNum[Byte].map(_.toLong.nanos))
    Arbitrary(
      for
        initial <- arbitrary[A]
        tail <- arbitrary[List[(FiniteDuration, A)]]
      yield TestSignal(initial, tail)
    )

  given [A: Eq](using Eq[IO[List[(A, FiniteDuration)]]]): Eq[Signal[IO, A]] = Eq.by { sig =>
    IO.ref(List.empty[(A, FiniteDuration)]).flatMap { ref =>
      TestControl.executeEmbed(
        sig
          .discrete
          .evalMap(IO.realTime.tupleLeft(_))
          .evalMap(x => ref.update(_ :+ x))
          .compile
          .drain,
        seed = Some(scalaCheckInitialSeed)
      ) *> ref.get
    }
  }

  given Ticker = Ticker()

  checkAll("Signal", ApplicativeTests[Signal[IO, _]].applicative[Int, Int, Int])

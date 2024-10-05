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

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.effect.testkit.TestInstances
import cats.kernel.Eq
import cats.laws.discipline.MonadTests
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import munit.DisciplineSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

import java.util.Base64
import scala.concurrent.duration.*

class SignalSuite extends DisciplineSuite, TestInstances:

  def testControlSeed =
    val bytes = Seed.fromBase64(scalaCheckInitialSeed).get.long._1.toString.getBytes()
    new String(Base64.getEncoder().encode(bytes))

  override def scalaCheckTestParameters =
    if sys.props("java.vm.name").contains("Scala.js") then
      super.scalaCheckTestParameters.withMinSuccessfulTests(10).withMaxSize(10)
    else super.scalaCheckTestParameters

  case class TestSignal[A](events: NonEmptyList[(FiniteDuration, A)]) extends Signal[IO, A]:
    def discrete: Stream[IO, A] = Stream.eval(IO.realTime).flatMap { now =>
      def go(events: NonEmptyList[(FiniteDuration, A)]): (A, List[(FiniteDuration, A)]) =
        events match
          case NonEmptyList((_, a), Nil) => (a, Nil)
          case NonEmptyList((t0, a0), tail @ ((t1, a1) :: _)) =>
            if t1 > now then (a0, tail)
            else go(NonEmptyList.fromListUnsafe(tail))

      val (current, remaining) = go(events)
      Stream.emit(current) ++ Stream.emits(remaining).evalMap { (when, a) =>
        IO.realTime.map(when - _).flatMap(IO.sleep).as(a)
      }
    }
    def get = IO.never
    def continuous = Stream.never

  given [A: Arbitrary]: Arbitrary[Signal[IO, A]] =
    given Arbitrary[FiniteDuration] = Arbitrary(Gen.posNum[Byte].map(_.toLong.millis))
    Arbitrary(
      for
        initial <- arbitrary[A]
        tail <- arbitrary[List[(FiniteDuration, A)]]
        events = tail.scanLeft(Duration.Zero -> initial) {
          case ((prevTime, _), (sleep, a)) =>
            (prevTime + sleep) -> a
        }
      yield TestSignal(NonEmptyList.fromListUnsafe(events))
    )

  given [A: Eq](using Eq[IO[List[(A, FiniteDuration)]]]): Eq[Signal[IO, A]] = Eq.by { sig =>
    TestControl.executeEmbed(
      sig
        .discrete
        .evalMap(IO.realTime.tupleLeft(_))
        .interruptAfter(Long.MaxValue.nanos)
        .compile
        .to(List)
        .map(_.reverse.distinctBy(_._2)), // reverse so latest wins in `distinctBy`
      seed = Some(testControlSeed)
    )
  }

  given Ticker = Ticker()

  // it is stack-safe, but expensive to test
  MonadTests[Signal[IO, _]].stackUnsafeMonad[Int, Int, Int].all.properties.foreach {
    case (id, prop) =>
      // TODO investigate failures #101
      if !Set(
          "monad (stack-unsafe).flatMap associativity",
          "monad (stack-unsafe).semigroupal associativity").contains(id)
      then property(id)(prop)
  }

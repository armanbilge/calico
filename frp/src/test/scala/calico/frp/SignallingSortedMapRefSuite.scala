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
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*

class SignallingSortedMapRefSuite extends CatsEffectSuite:

  test("mapref - does not emit spurious events") {
    SignallingSortedMapRef[IO, Boolean, Int]
      .flatTap(_.set(SortedMap(false -> 0, true -> 0)))
      .flatMap { s =>

        val events =
          s(false).discrete.evalTap(_ => IO.sleep(1.seconds)).unNoneTerminate.compile.toList

        val updates =
          IO.sleep(1100.millis) *>
            s(false).update(_.map(_ + 1)) *>
            IO.sleep(1.second) *>
            s(true).update(_.map(_ + 1)) *>
            IO.sleep(1.seconds) *>
            s(false).update(_.map(_ + 1)) *>
            IO.sleep(1.seconds) *>
            s(false).set(None)

        TestControl
          .executeEmbed(updates.background.surround(events))
          .assertEquals(List(0, 1, 2))
      }
  }

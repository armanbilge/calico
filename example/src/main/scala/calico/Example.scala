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

import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import org.scalajs.dom.*

object Example extends IOWebApp:
  def render = div(
    h1("Let's count!"),
    Counter("Sheep", initialStep = 3)
  )

  def Counter(label: String, initialStep: Int) =
    SignallingRef[IO, Int](initialStep).product(Channel.unbounded[IO, Int]).toResource.flatMap {
      (stepRef, diffCh) =>

        val allowedSteps = List(1, 2, 3, 5, 10)

        div(
          p(
            "Step: ",
            select(
              value <-- stepRef.discrete.map(_.toString).renderable,
              onChange --> (_.mapToValue.evalMap(i => IO(i.toInt)).foreach(stepRef.set)),
              allowedSteps.map(step => option(value := step.toString, step.toString))
            )
          ),
          p(
            label + ": ",
            b(diffCh.stream.scanMonoid.map(_.toString).renderable),
            " ",
            button(
              "-",
              onClick --> {
                _.evalMap(_ => stepRef.get).map(-1 * _).foreach(diffCh.send(_).void)
              }
            ),
            button(
              "+",
              onClick --> (_.evalMap(_ => stepRef.get).foreach(diffCh.send(_).void))
            )
          )
        )
    }

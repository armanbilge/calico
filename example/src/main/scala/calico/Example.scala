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
import cats.effect.std.Random
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*

object Example extends IOWebApp:
  def render = Stream.fixedRate[IO](1.second).void.renderable.flatMap { tick =>
    Topic[Rx[IO, *], Unit]
      .toResource
      .flatTap(topic => tick.through(topic.publish).compile.drain.background)
      .render
      .flatMap { tick =>
        div(
          div(
            "Tick #: ",
            tick.subscribe(0).as(1).scanMonoid.map(_.toString)
          ),
          div(
            "Random #: ",
            Stream.eval(Random.scalaUtilRandom[Rx[IO, _]]).flatMap { random =>
              tick.subscribe(0).evalMap(_ => random.nextInt).map(_ % 100).map(_.toString)
            }
          )
        )
      }
  }

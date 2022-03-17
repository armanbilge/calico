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
import cats.effect.IO
import fs2.Stream

import scala.concurrent.duration.*

object Example extends IOWebApp:
  def render = p("hello", "bye")

  def helloBye =
    Stream("hello", "bye").repeat.chunkLimit(1).unchunks.debounce[IO](1.second).renderable

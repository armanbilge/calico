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

package not.calico

import calico.html.io.*
import calico.html.io.given
import calico.syntax.*
import cats.effect.*
import cats.syntax.all.*
import fs2.concurrent.*
import fs2.dom.*

class SyntaxSuite:

  def stringSignal: SignallingRef[IO, String] = ???
  def stringOptionSignal: SignallingRef[IO, Option[String]] = ???
  def nodeSignal: SignallingRef[IO, Resource[IO, fs2.dom.Node[IO]]] = ???
  def nodeOptionSignal: SignallingRef[IO, Option[Resource[IO, fs2.dom.Node[IO]]]] = ???

  def mod = (cls := "bar").toMod[Node[IO]]

  def signalModifiers =
    div(
      stringSignal,
      stringOptionSignal,
      nodeSignal,
      nodeOptionSignal
    ).flatTap(_.modify(cls := "foo")).flatTap(_.modify(mod))

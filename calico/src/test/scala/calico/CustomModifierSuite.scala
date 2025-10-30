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

import calico.html.CustomModifier
import calico.html.io.*
import calico.html.io.given
import cats.effect.*
import munit.*
import org.scalajs.dom

class CustomModifierSuite extends FunSuite:

  // These tests are compile-time checks; they should not evaluate any effects
  // or interact with the DOM at runtime. Constructing Resources is fine.

  test("basic custom modifier compiles with HTML DSL") {
    case class DataAttribute(value: String) extends CustomModifier[IO, dom.Element]:
      def apply(element: dom.Element): Resource[IO, Unit] =
        Resource.eval(IO.unit)

    val _ = div(DataAttribute("hello"), "world")
    assert(true)
  }

  test("custom modifier can be combined with other modifiers") {
    case class CustomClass(name: String) extends CustomModifier[IO, dom.Element]:
      def apply(element: dom.Element): Resource[IO, Unit] =
        Resource.eval(IO.unit)

    val _ = div(cls := "container", CustomClass("x"), "content")
    assert(true)
  }

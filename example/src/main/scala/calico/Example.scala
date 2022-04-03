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
import calico.widget.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import monocle.macros.GenLens

object Example extends IOWebApp:

  final case class Person(firstName: String, lastName: String, age: Int)
  final case class TwoPeople(one: Person, two: Person)

  def render =
    SigRef[IO].of(TwoPeople(Person("", "", 0), Person("", "", 0))).toResource.flatMap {
      persons =>
        div(
          div(
            h3("View"),
            Widget.view(persons.discrete)
          ),
          div(
            h3("Edit 1"),
            Widget.edit(persons.zoom(GenLens[TwoPeople](_.one)))
          ),
          div(
            h3("Edit 2"),
            Widget.edit(persons.zoom(GenLens[TwoPeople](_.two)))
          )
        )

    }

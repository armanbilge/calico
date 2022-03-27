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

object Example extends IOWebApp:

  final case class Person(firstName: String, lastName: String, age: Int)

  def render = SignallingRef[IO].of(Person("", "", 0)).toResource.flatMap { personRef =>
    personRef.discrete.renderableSignal.flatMap { personSig =>
      div(
        div(
          h3("View"),
          Widget.view(personSig.discrete)
        ),
        div(
          h3("Edit"),
          Widget.edit(personSig.discrete)(_.foreach(personRef.set(_)))
        )
      )
    }
  }

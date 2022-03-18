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

import scala.concurrent.duration.given

object Example extends IOWebApp:

  def validateEmail(email: String): Either[String, Unit] =
    if email.isEmpty then Left("Please fill out email")
    else if !email.contains('@') then Left("Invalid email!")
    else Right(())

  def render = Channel.unbounded[IO, String].toResource.flatMap { emailCh =>
    div(
      span(
        label("Your email: "),
        input(onInput --> (_.mapToValue.through(emailCh.sendAll)))
      ),
      emailCh
        .stream
        .debounce(1.second)
        .map(validateEmail)
        .map {
          case Left(err) => s"Error: $err"
          case Right(()) => "Email ok!"
        }
        .renderable
    )
  }

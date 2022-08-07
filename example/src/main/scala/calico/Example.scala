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
import calico.router.*
import calico.std.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import monocle.macros.GenLens
import org.http4s.Uri
import org.http4s.syntax.all.*

object Example extends IOWebApp:

  def render = History.make[IO, Unit].evalMap(Router(_)).flatMap { router =>
    (SignallingRef[IO].of(0), SignallingRef[IO].of(0)).tupled.toResource.flatMap {
      (helloCounter, countCounter) =>

        def helloUri(who: String) =
          uri"" +? ("page" -> "hello") +? ("who" -> who)

        def countUri(n: Int) =
          uri"" +? ("page" -> "count") +? ("n" -> n)

        val helloRoute = Routes.one[IO] {
          case uri if uri.query.params.get("page").contains("hello") =>
            uri.query.params.getOrElse("who", "world")
        } { who => Resource.eval(helloCounter.update(_ + 1)) *> div("Hello, ", who) }

        val countRoute = Routes.one[IO] {
          case uri if uri.query.params.get("page").contains("count") =>
            uri.query.params.get("n").flatMap(_.toIntOption).getOrElse(0)
        } { n =>
          Resource.eval(countCounter.update(_ + 1)) *>
            p(
              "Sheep: ",
              n.map(_.toString).discrete,
              " ",
              button(
                "+",
                onClick --> {
                  _.foreach(_ => n.get.map(i => countUri(i + 1)).flatMap(router.navigate))
                }
              )
            )
        }

        val content = (helloRoute |+| countRoute).toResource.flatMap(router.dispatch)

        div(
          p("Created hello page ", helloCounter.map(_.toString).discrete, " times."),
          p("Created count page ", countCounter.map(_.toString).discrete, " times."),
          h4("Navigation"),
          ul(
            List("Shaun", "Shirley", "Timmy", "Nuts").map { sheep =>
              li(
                a(
                  href := "#",
                  onClick --> (_.foreach(_ => router.navigate(helloUri(sheep)))),
                  s"Hello, $sheep"
                )
              )
            },
            li(
              a(
                href := "#",
                onClick --> (_.foreach(_ => router.navigate(countUri(0)))),
                "Let's count!"
              )
            )
          ),
          h4("Content"),
          content
        )
    }

  }

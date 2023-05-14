# Time

## Basic Interval Stream

```scala mdoc:js
import calico.*
import calico.html.io.{*, given}
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*

import scala.concurrent.duration.*

val app: Resource[IO, HtmlDivElement[IO]] =
  Stream.fixedRate[IO](1.second).as(1).scanMonoid.map(_.toString).holdOptionResource
    .flatMap { tick =>
      div(
        div("Tick #: ", tick),
        div(
          "Random #: ",
          Random.scalaUtilRandom[IO].toResource.flatMap { random =>
            tick.discrete
              .evalMap(_ => random.nextInt.map(i => (i % 100).toString))
              .holdOptionResource
          }
        )
      )
    }

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

## Delay

```scala mdoc:js
import calico.*
import calico.html.io.{*, given}
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*

import scala.concurrent.duration.*

val app: Resource[IO, HtmlDivElement[IO]] =
  Channel.unbounded[IO, Unit].toResource.flatMap { clickCh =>
    val alert = clickCh.stream >>
      (Stream.emit("Just clicked!") ++ Stream.sleep_[IO](500.millis) ++ Stream.emit(""))

    div(
      button(onClick --> (_.void.through(clickCh.sendAll)), "Click me"),
      alert.holdResource("")
    )
  }

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

## Debounce

```scala mdoc:js
import calico.*
import calico.html.io.{*, given}
import calico.syntax.*
import calico.unsafe.given
import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*

import scala.concurrent.duration.given

def validateEmail(email: String): Either[String, Unit] =
  if email.isEmpty then Left("Please fill out email")
  else if !email.contains('@') then Left("Invalid email!")
  else Right(())

val app: Resource[IO, HtmlDivElement[IO]] =
  Channel.unbounded[IO, String].toResource.flatMap { emailCh =>
    val validated = emailCh.stream.debounce(1.second).map(validateEmail)
    validated.holdOptionResource.flatMap { validatedSig =>
      div(
        span(
          label("Your email: "),
          input.withSelf { self =>
            onInput --> (_.evalMap(_ => self.value.get).through(emailCh.sendAll))
          }
        ),
        span(
          cls <-- Nested(validatedSig).map {
            case Left(_) => List("-error")
            case Right(_) => List("-success")
          }.value,
          Nested(validatedSig).map {
            case Left(err) => s"Error: $err"
            case Right(()) => "Email ok!"
          }.value
        )
      )
    }
  }

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

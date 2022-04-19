# Time

## Basic Interval Stream

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.effect.std.Random
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*

val app = Stream.fixedRate[IO](1.second).as(1).scanMonoid.holdOptionResource
  .flatMap { tick =>
    div(
      div(
        "Tick #: ",
        tick.discrete.unNone.map(_.toString)
      ),
      div(
        "Random #: ",
        Stream.eval(Random.scalaUtilRandom[IO]).flatMap { random =>
          tick.discrete.unNone.evalMap(_ => random.nextInt).map(_ % 100).map(_.toString)
        }
      )
    )
  }

app.renderInto(node).allocated.unsafeRunAndForget()
```

## Delay

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*

val app = Channel.unbounded[IO, Unit].toResource.flatMap { clickCh =>
  val alert = clickCh.stream >>
    (Stream.emit("Just clicked!") ++ Stream.sleep_[IO](500.millis) ++ Stream.emit(""))

  div(
    button(onClick --> (_.void.through(clickCh.sendAll)), "Click me"),
    alert
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

## Debounce

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.given

def validateEmail(email: String): Either[String, Unit] =
  if email.isEmpty then Left("Please fill out email")
  else if !email.contains('@') then Left("Invalid email!")
  else Right(())

val app = Channel.unbounded[IO, String].toResource.flatMap { emailCh =>
  val validated = emailCh.stream.debounce(1.second).map(validateEmail)
  validated.holdOptionResource.flatMap { validatedSig =>
    div(
      span(
        label("Your email: "),
        input(onInput --> (_.mapToTargetValue.through(emailCh.sendAll)))
      ),
      span(
        cls <-- validatedSig.discrete.unNone.map {
          case Left(_) => List("-error")
          case Right(_) => List("-success")
        },
        validatedSig.discrete.unNone.map {
          case Left(err) => s"Error: $err"
          case Right(()) => "Email ok!"
        }
      )
    )
  }
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

# Time

## Basic Interval Stream

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.std.Random
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*

val app = Stream.fixedRate[IO](1.second).void.renderable.flatMap { tick =>
  Topic[Rx[IO, _], Unit]
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

app.renderInto(node).allocated.unsafeRunAndForget()
```

## Delay

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*

val app = Channel.unbounded[IO, Unit].toResource.flatMap { clickCh =>
  val alert = clickCh.stream >>
    (Stream.emit("Just clicked!") ++ Stream.sleep_[IO](500.millis) ++ Stream.emit(""))

  div(
    button(onClick --> (_.void.through(clickCh.sendAll)), "Click me"),
    alert.renderable
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

## Debounce

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.given

def validateEmail(email: String): Either[String, Unit] =
  if email.isEmpty then Left("Please fill out email")
  else if !email.contains('@') then Left("Invalid email!")
  else Right(())

val app = Channel.unbounded[IO, String].toResource.flatMap { emailCh =>
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

app.renderInto(node).allocated.unsafeRunAndForget()
```

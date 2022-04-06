# Hello World

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import fs2.*
import fs2.concurrent.*

val app = SigRef[IO].of("world").toResource.flatMap { nameRef =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      onInput --> (_.mapToTargetValue.foreach(nameRef.set))
    ),
    span(" Hello, ", nameRef.discrete.map(_.toUpperCase))
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```
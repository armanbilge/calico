# Hello World

```scala mdoc:js
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import fs2.*
import fs2.concurrent.*
import org.scalajs.dom.*

val app = SignallingRef[IO, String]("world").toResource.flatMap { nameRef =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      onInput --> (_.mapToValue.foreach(nameRef.set))
    ),
    span(
      "Hello, ",
      nameRef.discrete.map(_.toUpperCase).renderable
    )
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

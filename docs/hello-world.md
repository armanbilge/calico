# Hello World

```scala mdoc:js
import calico.*
import calico.unsafe.given
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import fs2.*
import fs2.concurrent.*

val app = SignallingRef[IO].of("world").toResource.flatMap { name =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      onInput --> (_.mapToTargetValue.foreach(name.set))
    ),
    span(" Hello, ", name.discrete.map(_.toUpperCase))
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

# Hello World

```scala mdoc:js
import calico.*
import calico.html.io.{*, given}
import calico.unsafe.given
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import fs2.*
import fs2.concurrent.*

val app = SignallingRef[IO].of("world").toResource.flatMap { name =>
  div(
    label("Your name: "),
    input.withSelf { self =>
      (
        placeholder := "Enter your name here",
        // here, input events are run through the given Pipe
        // this starts background fibers within the lifecycle of the <input> element
        onInput --> (_.foreach(_ => self.value.get.flatMap(name.set)))
      )
    },
    span(" Hello, ", name.map(_.toUpperCase))
  )
}

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).allocated.unsafeRunAndForget()
```

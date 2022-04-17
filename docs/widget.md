# Widgets

```scala mdoc:js
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import calico.unsafe.given
import calico.widget.*
import cats.effect.*
import cats.effect.syntax.all.*
import fs2.*
import fs2.concurrent.*

case class Person(firstName: String, lastName: String, age: Int)

val app = SignallingRef[IO].of(Person("", "", 0)).toResource.flatMap { person =>
  div(
    div(h3("View"), Widget.view(person)),
    div(h3("Edit"), Widget.edit(person))
  )
}

app.renderInto(node).allocated.unsafeRunAndForget()
```

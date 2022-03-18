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

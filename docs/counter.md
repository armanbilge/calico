# Counter

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

def Counter(label: String, initialStep: Int) =
  SigRef[IO].of(initialStep).product(Channel.unbounded[IO, Int])
    .toResource.flatMap { (stepRef, diffCh) =>

      val allowedSteps = List(1, 2, 3, 5, 10)

      div(
        p(
          "Step: ",
          select(
            value <-- stepRef.discrete.map(_.toString),
            onChange --> {
              _.mapToTargetValue.map(_.toIntOption).unNone.foreach(stepRef.set)
            },
            allowedSteps.map(step => option(value := step.toString, step.toString))
          )
        ),
        p(
          label + ": ",
          b(diffCh.stream.scanMonoid.map(_.toString).renderable),
          " ",
          button(
            "-",
            onClick --> {
              _.evalMap(_ => stepRef.getF).map(-1 * _).foreach(diffCh.send(_).void)
            }
          ),
          button(
            "+",
            onClick --> (_.evalMap(_ => stepRef.getF).foreach(diffCh.send(_).void))
          )
        )
      )
  }

val app = div(
  h1("Let's count!"),
  Counter("Sheep", initialStep = 3)
)

app.renderInto(node).allocated.unsafeRunAndForget()
```

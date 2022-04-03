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
    .toResource.flatMap { (step, diff) =>

      val allowedSteps = List(1, 2, 3, 5, 10)

      div(
        p(
          "Step: ",
          select(
            allowedSteps.map(step => option(value := step.toString, step.toString)),
            value <-- step.discrete.map(_.toString),
            onChange --> {
              _.mapToTargetValue.map(_.toIntOption).unNone.foreach(step.set)
            }
          )
        ),
        p(
          label + ": ",
          b(diff.stream.scanMonoid.map(_.toString).renderable),
          " ",
          button(
            "-",
            onClick --> {
              _.evalMap(_ => step.getF).map(-1 * _).foreach(diff.send(_).void)
            }
          ),
          button(
            "+",
            onClick --> (_.evalMap(_ => step.getF).foreach(diff.send(_).void))
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

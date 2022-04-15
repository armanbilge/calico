{% laika.title = Introduction %}

# calico

**calico** is an (early-stage) "framework" for building web applications in [Scala.js](https://www.scala-js.org/) with [Cats Effect 3](https://typelevel.org/cats-effect/) and [fs2](https://fs2.io/). I say "framework" because **calico** no new concepts of its own; it is effectively a DSL. If you enjoy working with Cats Effect and fs2 then (I hope) you will like **calico** as well.

### Acknowledgements
**calico** is heavily inspired by [Laminar](https://Laminar.dev/). I have yet only had time to plagiarize the DSL and a few of the examples ;)

### Try it!

```scala
libraryDependencies += "com.armanbilge" %%% "calico" % "@VERSION@"
```

## Core concepts

### Components and resource management

The most important idea behind **calico** is that each component of your app (and in fact your app itself) should be expressed as a `Resource[IO, HTMLElement]`.

```scala
import cats.effect.*
import org.scalajs.dom.*
// note: no calico import yet!

val component: Resource[IO, HTMLElement] = ???

// or more generally:
def component[F[_]: Async]: Resource[F, HTMLElement] = ???
```

This `Resource` completely manages the lifecycle of that element and its children. When the `Resource` is allocated, it will create an instance of the `HTMLElement` and any supporting resources, such as background `Fiber`s or WebSocket connections. In kind, when the `Resource` is closed, these `Fiber`s and connections are canceled and released.

Because `Resource[IO, HTMLElement]` is referentially-transparent, it naturally behaves as a "builder". Your component can be re-used in multiple places in your application as well as un-mounted and re-mounted without worrying about crossed-wires or leaked resources. This makes it easy to compose components.

So far, none of this is specific to **calico**: we get all of this for free from Cats Effect. **calico** steps in with a friendly DSL to cut down the boilerplate.
```scala mdoc:js:compile-only
import calico.dsl.io.*
import cats.effect.*
import org.scalajs.dom.*

val component: Resource[IO, HTMLElement] = div(i("hello"), " ", b("world"))
```

Yes, in this very unexciting example `i("hello")` and `b("world")` are both `Resource`s that monadically compose with `div(...)` to create yet another `Resource`! There are no other resources involved in this very simple snippet. Also note that we have not yet _created_ any `HTMLElement`s, we have merely created a `Resource` that _describes_ how to make one.

A more interesting example is this interactive Hello World demo.

```scala mdoc:js:shared
import calico.*
import calico.dsl.io.*
import calico.syntax.*
import cats.effect.*
import cats.effect.syntax.all.*
import fs2.*
import fs2.concurrent.*

val component = SigRef[IO].of("world").toResource.flatMap { nameRef =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      // here, input events are run through the given Pipe
      // this starts background fibers within the lifecycle of the <input> element
      onInput --> (_.mapToTargetValue.foreach(nameRef.set))
    ),
    span(
      " Hello, ",
      // here, a Stream is rendered into the HTML
      // this starts background fibers within the life cycle of the <span> element
      nameRef.discrete.map(_.toUpperCase)
    )
  )
}
```

```scala mdoc:js:invisible
component.renderInto(node).allocated.unsafeRunAndForget()(calico.unsafe.given_IORuntime)
```

The ideas are very much the same as the prior example.

1. `input(...)` is a `Resource` that creates an `<input>` element and also manages `Fiber`s that handle input events. 
2. `span(...)` is a `Resource` that creates a `<span>` element and also manages `Fiber`s that handle rendering of the name.
3. `div(...)` is a `Resource` composed of the `input(...)` and `span(...)` `Resource`s, and therefore (indirectly) manages the `Fiber`s of its child components.

And there we have it: a self-contained component consisting of non-trivial resources, that can be safely used, reused, and torn down.

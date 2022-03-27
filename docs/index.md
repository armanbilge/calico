{% laika.title = Introduction %}

# calico

**calico** is an (early-stage) "framework" for building web applications in [Scala.js](https://www.scala-js.org/) with [Cats Effect 3](https://typelevel.org/cats-effect/) and [fs2](https://fs2.io/). I say "framework" because **calico** (so far) introduces only one new concept of its own; otherwise, it is almost entirely a DSL. If you enjoy working with Cats Effect and fs2 then (I hope) you will like **calico** as well.

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

val component = SignallingRef[IO, String]("world").toResource.flatMap { nameRef =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      // here, input events are run through the given Pipe
      // this starts background fibers within the lifecycle of the <input> element
      onInput --> (_.mapToValue.foreach(nameRef.set))
    ),
    span(
      " Hello, ",
      // here, a Stream is rendered into the HTML
      // this starts background fibers within the life cycle of the <span> element
      nameRef.discrete.map(_.toUpperCase).renderable
    )
  )
}
```

```scala mdoc:js:invisible
component.renderInto(node).allocated.unsafeRunAndForget()(cats.effect.unsafe.IORuntime.global)
```

The ideas are very much the same as the prior example.

1. `input(...)` is a `Resource` that creates an `<input>` element and also manages `Fiber`s that handle input events. 
2. `span(...)` is a `Resource` that creates a `<span>` element and also manages `Fiber`s that handle rendering of the name.
3. `div(...)` is a `Resource` composed of the `input(...)` and `span(...)` `Resource`s, and therefore (indirectly) manages the `Fiber`s of its child components.

And there we have it: a self-contained component consisting of non-trivial resources, that can be safely used, reused, and torn down.

### Glitch-free rendering

As mentioned in the introduction, **calico** does introduce one new concept: the `Rx` monad. Its sole purpose is to provide a mechanism for glitch-free rendering.

Consider our Hello World example: suppose that in addition to displaying the entered name, we also wanted to display its length. A rendering glitch would be if the UI updates the length before updating the name such that (very briefly!) the user sees inconsistent state.

The `Rx` monad is so-called for its "highly reactive" semantics. Specifically, any computation occurring in `Rx` (notably, updating the DOM) is _guaranteed_ to complete before the UI re-renders. For this reason, the **calico** DSL uses `Rx` as the effect type for `Stream`s that bind the DOM to dynamic content.

There are currently two easy ways to transform a `stream: Stream[F, A]` to the `Rx` effect and thus make it "renderable". The type signatures look intimidating, but (1) was already used in the example above and (2) is used in the example below.

1. `stream.renderable: Resource[F, Stream[Rx[F, _], A]]`
   
   A one-off to make a single-use `Stream` renderable.

2. `stream.renderableSignal: Resource[F, Signal[Rx[F, _], A]]`

   Creates a `Signal` which can have multiple renderable subscribers.

Now, we can display the input length in our Hello World without any glitches!

```scala mdoc:js:shared
val component2 = SignallingRef[IO, String]("world").toResource.flatMap { nameRef =>
  nameRef.discrete.renderableSignal.flatMap { nameSig =>
    div(
      label("Your name: "),
      input(
        placeholder := "Enter your name here",
        onInput --> (_.mapToValue.foreach(nameRef.set))
      ),
      span(
        " Hello, ",
        nameSig.discrete.map(_.toUpperCase)
      ),
      p("Length: ", nameSig.discrete.map(_.length.toString))
    )
  }
}
```

```scala mdoc:js:invisible
component2.renderInto(node).allocated.unsafeRunAndForget()(cats.effect.unsafe.IORuntime.global)
```

#### `Rx` monad, behind-the-scenes

The `Rx` monad is in fact a transformer for a monad `F[_]` that implements `Async[F]`.
```scala
opaque type Rx[F[_], A] = F[A]
```

Despite appearances, it is _not_ an identity transformation.

1. `Rx` execution is scheduled on the [microtask queue](https://javascript.info/event-loop#macrotasks-and-microtasks). This is because the JavaScript event loop will always prioritize microtasks over rendering, timers, and handling of user and I/O events.
2. `Rx` only implements `Concurrent` and `Sync`. It does not implement `Temporal` nor `Async`, because timers and external async events are not microtasks.

Although the semantics of the microtask queue are [unsuitable for a general-purpose execution context](https://github.com/scala-js/scala-js-macrotask-executor#background), selectively applying them to the `Rx` monad is a useful strategy to achieve glitch-free rendering.
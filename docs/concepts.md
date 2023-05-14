# Core Concepts

All of **Calico**’s core concepts are actually inherited from Cats Effect and FS2. This page highlights them and demonstrates how they are applied in the context of building web applications.

## Components and resource management

The most important idea behind **Calico** is that each component of your app (and in fact your app itself) should be expressed as a `Resource[IO, HtmlElement[IO]]`.

```scala
import cats.effect.*
import fs2.dom.*
// note: no calico import yet!

val component: Resource[IO, HtmlElement[IO]] = ???

// or more generally:
def component[F[_]: Dom]: Resource[F, HtmlElement[F]] = ???
```

This `Resource` completely manages the lifecycle of that element and its children. When the `Resource` is allocated, it will create an instance of the `HtmlElement` and any supporting resources, such as background `Fiber`s or WebSocket connections. In kind, when the `Resource` is closed, these `Fiber`s and connections are canceled and released.

Because `Resource[IO, HtmlElement[IO]]` is referentially-transparent, it naturally behaves as a "builder". Your component can be re-used in multiple places in your application as well as un-mounted and re-mounted without worrying about crossed-wires or leaked resources. This makes it easy to compose components.

So far, none of this is specific to **Calico**: we get all of this for free from Cats Effect and FS2 DOM. **Calico** provides an idiomatic DSL for describing components with standard HTML tags and attributes.
```scala mdoc:js:compile-only
import calico.html.io.{*, given}
import cats.effect.*
import fs2.dom.*

val component: Resource[IO, HtmlElement[IO]] = div(i("hello"), " ", b("world"))
```

Yes, in this very unexciting example `i("hello")` and `b("world")` are both `Resource`s that monadically compose with `div(...)` to create yet another `Resource`! There are no other resources involved in this very simple snippet. Also note that we have not yet _created_ any `HtmlElement`s, we have merely created a `Resource` that _describes_ how to make one.

A more interesting example is this interactive Hello World demo.

```scala mdoc:js:shared
import calico.*
import calico.html.io.{*, given}
import calico.syntax.*
import cats.effect.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*

val component: Resource[IO, HtmlDivElement[IO]] =
  SignallingRef[IO].of("world").toResource.flatMap { name =>
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
      span(
        " Hello, ",
        // here, a Signal is rendered into the HTML
        // this starts background fibers within the life cycle of the <span> element
        name.map(_.toUpperCase)
      )
    )
  }
```

```scala mdoc:js:invisible
import calico.unsafe.given
component.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

The ideas are very much the same as the prior example.

1. `input(...)` is a `Resource` that creates an `<input>` element and also manages `Fiber`s that handle input events. 
2. `span(...)` is a `Resource` that creates a `<span>` element and also manages `Fiber`s that handle rendering of the name.
3. `div(...)` is a `Resource` composed of the `input(...)` and `span(...)` `Resource`s, and therefore (indirectly) manages the `Fiber`s of its child components.

And there we have it: a self-contained component consisting of non-trivial resources, that can be safely used, reused, and torn down.

## Signals

In the Hello World demo above, we glossed over the `SignallingRef` used to hold the component’s state. A `SignallingRef` is a Cats Effect `Ref` (i.e. a mutable variable) that is also an FS2 `Signal`.

A `Signal` is a time-varying value. You can always obtain its current value, and you may also subscribe to a stream of update events that notify when it is modified. This is ideal for use in UI components: they can always render immediately with the current value, and re-render only when there are updates.

`Signal` is a monad, enabling them to be transformed with pure functions and composed with each other. Using transformation and composition, you can derive a `Signal` that contains precisely the data you are interested in.

```scala mdoc:js:shared
import cats.syntax.all.*
import calico.frp.given

enum Cardinal:
  case North, South

val signals = (
  SignallingRef[IO].of(Option.empty[Cardinal]),
  SignallingRef[IO].of(""),
  SignallingRef[IO].of(""),
).tupled.toResource

val app: Resource[IO, HtmlDivElement[IO]] =
  signals.flatMap { (cardinalSig, northSig, southSig) =>
    div(
      div(
        label("North input: "),
        input.withSelf { self =>
          onInput --> (_.foreach(_ => self.value.get.flatMap(northSig.set)))
        },
      ),
      br(()),
      div(
        select.withSelf { self =>
          (
            option(disabled := true, selected := true, "Select input"),
            option(value := "north", "North"),
            option(value := "south", "South"),
            onChange --> (
              _.foreach(_ => self.value.get.map {
                case "north" => Some(Cardinal.North)
                case "south" => Some(Cardinal.South)
                case _ => None
              }.flatMap(cardinalSig.set(_)))
            )
          )
        },
        " ",
        // compose cardinal signal with appropriate input signal
        (cardinalSig: Signal[IO, Option[Cardinal]]).flatMap {
          case Some(Cardinal.North) => northSig
          case Some(Cardinal.South) => southSig
          case None => Signal.constant("")
        }
      ),
      br(()),
      div(
        label("South input: "),
        input.withSelf { self =>
          onInput --> (_.foreach(_ => self.value.get.flatMap(southSig.set)))
        },
      ),
    )
  }
```

```scala mdoc:js:invisible
import calico.unsafe.given
app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

There are various ways to obtain a `Signal`.

- Create a `SignallingRef` with an initial value.
```scala
SignallingRef[IO].of("initial value")
```

- Derive a `Signal` from a `Stream`, by “holding” its latest value.
```scala
def stringStream: Stream[IO, String] = ???
stringStream.holdResource("initial value")
stringStream.holdOptionResource // use None for the intitial value
```

## Task scheduling and glitch-free rendering

A JavaScript webapp typically has a flow like:

1. An event fires. Examples:
  - a user event, such a button click
  - a scheduled timer event
  - an I/O event, such as an HTTP response or WebSocket message
2. An event handler is triggered, starting (potentially concurrent) tasks to update the application state and the UI. These tasks may also setup new event emitters, for example by scheduling timers or initiating an HTTP request.
3. The UI re-renders.

**Calico** is highly-optimized for this pattern and by default schedules all tasks as so-called _microtasks_. These microtasks have very high-priority: while there is still work to be done, the UI will not re-render and no further events will be processed. Only once all microtasks are complete, will the UI re-render and events will start being processed again.

Notice that this scheduling strategy guarantees glitch-free rendering. Because all tasks triggered by an event must complete before the view re-renders, the user will never see inconsistent state in the UI.

However, there are certain situations where you may want the browser to re-render in the middle of a task. In these cases, simply sequence an `IO.cede` operation. This will temporarily yield control flow back to the browser so that it may re-render the UI, before resuming the task.

```scala
updateComponentA *> // doesn't render yet
  updateComponentB *> // still didn't render
  IO.cede *> // re-render now
  doOtherStuff *> ... // do non-view-related work
```

Explicitly inserting an `IO.cede` can be a useful strategy to improve your app’s UX, by re-rendering as soon as you are done updating the view, and deferring other work until after the re-render. This will make your UI more responsive.

To learn more I recommend [this article about the JavaScript event loop](https://javascript.info/event-loop).

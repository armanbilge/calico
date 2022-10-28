{% laika.title = Introduction %}

# Calico

**Calico** is a UI library for the [Typelevel.js](https://typelevel.org/) ecosystem. It leverages the abstractions provided by [Cats Effect](https://typelevel.org/cats-effect/) and [FS2](https://fs2.io/) to provide a fluent DSL for building web applications that are composable, reactive, and safe. If you enjoy working with Cats Effect and FS2 then I hope that you will like **Calico** as well.

### Acknowledgements

**Calico** was inspired by [Laminar](https://Laminar.dev/). I have yet only had time to plagiarize the DSL and a few of the examples ;) Thanks to [@raquo](https://github.com/raquo/) for sharing their wisdom.

I am very grateful to [@SystemFw](https://github.com/SystemFw/) who gave me a tutorial on all things [Functional Reactive Programming](https://en.wikipedia.org/wiki/Functional_reactive_programming) shortly before I embarked on this project.

### Try it!

With special thanks to [@yurique](https://github.com/yurique/), you can now try **Calico** right in your browser at [scribble.ninja](https://scribble.ninja/)!

```scala
libraryDependencies += "com.armanbilge" %%% "calico" % "@VERSION@"
```

Please open issues (and PRs!) for anything and everything :)

### Integrations

- [calico-smithy4s-demo](https://github.com/kubukoz/calico-smithy4s-demo/) showcases a fullstack app and integration with [smithy4s](https://github.com/disneystreaming/smithy4s/). Thank you [@kubukoz](https://github.com/kubukoz/)!

## Core concepts

### Components and resource management

The most important idea behind **Calico** is that each component of your app (and in fact your app itself) should be expressed as a `Resource[IO, HTMLElement]`.

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

So far, none of this is specific to **Calico**: we get all of this for free from Cats Effect. **Calico** steps in with a friendly DSL to cut down the boilerplate.
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

val component = SignallingRef[IO].of("world").toResource.flatMap { name =>
  div(
    label("Your name: "),
    input(
      placeholder := "Enter your name here",
      // here, input events are run through the given Pipe
      // this starts background fibers within the lifecycle of the <input> element
      onInput --> (_.mapToTargetValue.foreach(name.set))
    ),
    span(
      " Hello, ",
      // here, a Stream is rendered into the HTML
      // this starts background fibers within the life cycle of the <span> element
      name.discrete.map(_.toUpperCase)
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

### Task scheduling and glitch-free rendering

A JavaScript webapp typically has a flow like:

1. An event fires. Examples:
  - a user event, such a button click
  - a scheduled timer event
  - an I/O event, such as an HTTP response or WebSocket message
2. An event handler is triggered, starting (potentially concurrent) tasks to update the application state and the UI. These tasks may also setup new event emitters, for example by scheduling timers or initiating an HTTP request.
3. The UI re-renders.

**Calico** is highly-optimized for this pattern and by default schedules all tasks as so-called _microtasks_. These microtasks have very high-priority: while there is still work to be done, the UI will not re-render and no further events will be processed. Only once all microtasks are complete, will the UI re-render and events will start being processed again.

Notice that this scheduling strategy guarantees glitch-free rendering. Because all tasks triggered by an event must complete before the view re-renders, the user will never see inconsistent state in the UI.

However, there are certain situations where running a task with high-priority may not be desirable and you would prefer that it runs in the "background" while your application continues to be responsive. This is relevant only if you are doing an expensive calculation or processing task; for example, there is no need to explicitly background I/O tasks since they operate via the event-driven flow described above.

In these cases, you should break that expensive task into smaller steps and schedule it as a so-called _macrotask_:

```scala
import calico.unsafe.MacrotaskExecutor

val expensiveTask = IO(smallStep1()) *> IO(smallStep2()) *> IO(smallStep3()) *> ...
expensiveTask.evalOn(MacrotaskExecutor)
```

The [`MacrotaskExecutor`](https://github.com/scala-js/scala-js-macrotask-executor) schedules macrotasks with equal priority to event processing and UI rendering. Conceptually, it is somewhat analogous to using `IO.blocking(...)` on the JVM, in that running these tasks on a separate `ExecutionContext` preserves fairness and enables your application to continue responding to incoming events. Conversely, forgetting to use `.evalOn(MacrotaskExecutor)` or `IO.blocking(...)` could cause your application to become unresponsive.

However, I suspect situations where you need to use the `MacrotaskExecutor` in webapp are rare. If you truly have a long-running, compute-intensive task that you do not want to compromise the responsiveness of your application, you should consider running it in a background thread via a [WebWorker](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API) instead.

To learn more about microtasks and macrotasks I recommend [this article about the JavaScript event loop](https://javascript.info/event-loop).

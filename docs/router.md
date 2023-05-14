# Router

@:callout(info)

[frontroute] is a full-featured routing library, that was originally developed for Laminar and now supports **Calico** as well. It also has extensive documentation and examples, so be sure to [check it out][frontroute]!

@:@

**Calico** offers a simple router as an optional module. A router helps you build a single-page application composed of sub-pages with distinct URLs and support for session history (e.g., back/forward). It is implemented via the [History](https://developer.mozilla.org/en-US/docs/Web/API/History) API.

```scala
libraryDependencies += "com.armanbilge" %%% "calico-router" % "@VERSION@"
```

`calico-router` is built only with Cats Effect, FS2-DOM, and http4s, and has no dependency on `calico` core, such that it is framework-agnostic. Integration with **Calico** is seamless.

Special thanks to [@jberggg](https://github.com/jberggg) whose [fs2-spa-router](https://github.com/jberggg/fs2-spa-router) inspired this project.

## Concepts and demo

The `Router` API offers methods for interacting with the browser location and session history and for dispatching `Routes` into components that can be placed in your application. It is possible to dispatch multiple `Routes` within a single application and even nest them arbitrarily.

The `Routes.one` method can be used to create a single "endpoint" by:

1. `match`ing on an http4s `Uri`
2. extracting some state `A` from the `Uri`
3. building a component with a `Signal` of `A`

This design enables an important optimization: when navigating to a new location, if the URI matches the currently rendered endpoint, then the existing component is reused and the `Signal` propagates any changes to dynamic content. This is demonstrated in the demo below where a pair of counters track every time an endpoint is re-rendered from scratch.

Endpoints are combined via the `|+|` operator, because `Routes` form a monoid.

In the following demo all routing is done by matching on the query portion of the URI since this is compatible with GitHub Pages hosting. A production application can match on the path portion for a friendlier UX.

```scala mdoc:js
import calico.html.io.{*, given}
import calico.router.*
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.syntax.all.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*
import org.http4s.*
import org.http4s.syntax.all.*

val app: Resource[IO, HtmlDivElement[IO]] =
  Router(Window[IO]).toResource.flatMap { router =>
    (SignallingRef[IO].of(0), SignallingRef[IO].of(0)).tupled.toResource.flatMap {
      (helloCounter, countCounter) =>

        def helloUri(who: String) =
          uri"" +? ("page" -> "hello") +? ("who" -> who)

        def countUri(n: Int) =
          uri"" +? ("page" -> "count") +? ("n" -> n)

        val helloRoute = Routes.one[IO] {
          case uri if uri.query.params.get("page").contains("hello") =>
            uri.query.params.getOrElse("who", "world")
        } { who => helloCounter.update(_ + 1).toResource *> div("Hello, ", who) }

        val countRoute = Routes.one[IO] {
          case uri if uri.query.params.get("page").contains("count") =>
            uri.query.params.get("n").flatMap(_.toIntOption).getOrElse(0)
        } { n =>
          countCounter.update(_ + 1).toResource *>
            p(
              "Sheep: ",
              n.map(_.toString),
              " ",
              button(
                "+",
                onClick --> {
                  _.foreach(_ => n.get.map(i => countUri(i + 1)).flatMap(router.navigate))
                }
              )
            )
        }

        val content = (helloRoute |+| countRoute).toResource.flatMap(router.dispatch)

        div(
          p("Created hello page ", helloCounter.map(_.toString), " times."),
          p("Created count page ", countCounter.map(_.toString), " times."),
          h3("Navigation"),
          p("Watch the URL change in your browser address bar!"),
          ul(
            List("Shaun", "Shirley", "Timmy", "Nuts").map { who =>
              li(
                a(
                  href := "#",
                  onClick --> (_.foreach(_ => router.navigate(helloUri(who)))),
                  s"Hello, $who"
                )
              )
            },
            li(
              a(
                href := "#",
                onClick --> (_.foreach(_ => router.navigate(countUri(0)))),
                "Let's count!"
              )
            )
          ),
          h3("Content"),
          content
        )
    }
  }

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

[frontroute]: https://frontroute.dev/v/0.17.x-calico/

# IOWebApp

`IOWebApp` is a convenient way to define the entry point to your web application. It is the analog to Cats Effect `IOApp` for the browser.

```scala mdoc:js:compile-only
import calico.*
import calico.html.io.{*, given}
import cats.effect.*
import fs2.dom.*

object MyCalicoApp extends IOWebApp:
  def render: Resource[IO, HtmlElement[IO]] =
    div("Toto, I've a feeling we're not in Kansas anymore.")
```

Your `build.sbt` should include:
```scala
scalaJSUseMainModuleInitializer := true
```

And your `index.html` should look something like this:

```html
<html>
  <body>
    <div id="app"></div>
    <script src="main.js"></script>
  </body>
</html>
```

You can customize the `id` of your application’s root element.

```scala
override def rootElementId = "somewhere-over-the-rainbow"
```

`IOWebApp` also provides a `window: fs2.dom.Window[IO]` to access various Web APIs.

```scala
def render =
  window.location.href.get.toResource.flatMap { location =>
    div(s"Welcome to $location")
  }
```

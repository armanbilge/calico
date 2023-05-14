# http4s

These examples demonstrate how to integrate with [http4s-dom](https://http4s.github.io/http4s-dom/).

```scala
libraryDependencies += "org.http4s" %%% "http4s-dom" % "@HTTP4S_DOM_VERSION@"
```

## HTTP Request

```scala mdoc:js
import calico.html.io.{*, given}
import calico.syntax.*
import calico.unsafe.given
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.*
import fs2.dom.*
import io.circe.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dom.*

case class Repo(stargazers_count: Int) derives Decoder
object Repo:
  given EntityDecoder[IO, Repo] = jsonOf

val client = FetchClientBuilder[IO].create

val app: Resource[IO, HtmlDivElement[IO]] = (
  input(size := 36, typ := "text", value := "armanbilge/calico"),
  SignallingRef[IO].of("").toResource
).flatMapN { (repoInput, starsResult) =>

  val countStars: IO[Unit] =
    starsResult.set(" counting ... ") *>
      repoInput.value.get
        .flatMap { repo =>
          client.expect[Repo](s"https://api.github.com/repos/$repo").attempt
        }
        .flatMap {
          case Right(Repo(stars)) => starsResult.set(s"$stars ★")
          case Left(_) => starsResult.set(s"Not found :(")
        }

  div(
    h3("How many stars?"),
    repoInput,
    button(
      // switchMap cancels an ongoing request if the button is clicked again
      onClick --> (_.switchMap(_ => Stream.exec(countStars))),
      "Count"
    ),
    span(
      styleAttr := "margin-left: 1em; color: var(--secondary-color)",
      starsResult
    )
  )
}

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

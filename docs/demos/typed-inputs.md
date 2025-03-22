# Typed Inputs Demo

This demo shows how to use Calico's typed inputs for better type safety and validation.

```scala mdoc:js
import calico.*
import calico.html.io.{*, given}
import calico.html.TypedInput
import calico.unsafe.given
import calico.syntax.*
import cats.effect.*
import fs2.*
import fs2.concurrent.*
import fs2.dom.*
import java.time.LocalDate

case class FormData(
  name: String,
  email: String,
  age: Double,
  birthDate: LocalDate
)

val app: Resource[IO, HtmlDivElement[IO]] =
  for {
    nameInput <- TypedInput.text[IO].render
    emailInput <- TypedInput.email[IO].render
    ageInput <- TypedInput.number[IO].render
    dateInput <- TypedInput.date[IO].render
    formData <- SignallingRef[IO].of(Option.empty[FormData]).toResource

    // Create channels for each input
    nameCh <- Channel.unbounded[IO, String].toResource
    emailCh <- Channel.unbounded[IO, String].toResource
    ageCh <- Channel.unbounded[IO, Double].toResource
    dateCh <- Channel.unbounded[IO, LocalDate].toResource

    // Subscribe to input events
    _ <- Resource.eval(
      nameInput.events.foreach(nameCh.send) ++
      emailInput.events.foreach(emailCh.send) ++
      ageInput.events.foreach(ageCh.send) ++
      dateInput.events.foreach(dateCh.send)
    ).compile.drain.background

    // Combine all inputs into FormData
    _ <- Resource.eval(
      (
        nameCh.stream,
        emailCh.stream,
        ageCh.stream,
        dateCh.stream
      ).parMapN(FormData.apply).foreach(data => formData.set(Some(data)))
    ).compile.drain.background

    div <- div(
      h1("Typed Inputs Demo"),
      div(
        label("Name: "), nameInput,
        br,
        label("Email: "), emailInput,
        br,
        label("Age: "), ageInput,
        br,
        label("Birth Date: "), dateInput
      ),
      div(
        h2("Form Data:"),
        pre(
          formData.map(_.fold("No data yet")(data => 
            s"""Name: ${data.name}
               |Email: ${data.email}
               |Age: ${data.age}
               |Birth Date: ${data.birthDate}""".stripMargin
          ))
        )
      )
    )
  } yield div

app.renderInto(node.asInstanceOf[fs2.dom.Node[IO]]).useForever.unsafeRunAndForget()
```

The demo above shows:

1. How to create typed inputs for different data types (String, Double, LocalDate)
2. How to handle input events with proper type safety
3. How to combine multiple typed inputs into a form
4. How to validate inputs (email format) 
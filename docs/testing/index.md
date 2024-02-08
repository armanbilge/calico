# Testing guide
Developing unit testing in Calico components can be achieved using your preferred testing framework that supports Cats Effects. 
It is essential to ensure the proper setup of `jsEnv` as an additional requirement.

## Compatible libraries
- Scalatest, Specs2, minitest, µTest, and scalacheck can be utilized with an additional dependency on [`cats-effect-testing`](https://github.com/typelevel/cats-effect-testing).
- [`munit-cats-effect`](https://github.com/typelevel/munit-cats-effect) is a library that provides a Cats Effect 3 integration for MUnit

## `jsEnv` setup
The primary target runtime of Calico is a standard web browser. 
Therefore, it is essential to ensure that the `jsEnv` is set up correctly.
Scalajs provides a variety of `jsEnv` implementations that can be used for testing like
`Node.js` or `JSDOMNodeJSEnv`. 
Environments based on `Selenium` or `Playwright` can also be used which offer real browser testing. 
Please refer to the [Scala.js documentation](https://www.scala-js.org/doc/project/js-environments.html) for more information.

## Using `JSDOMNodeJSEnv`
The [scala-js-env-jsdom-nodejs](https://github.com/scala-js/scala-js-env-jsdom-nodejs) does not support `ESModule` and `CommonJS` modules. 
Add the following to your `build.sbt`:
```scala
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.NoModule)
}
```
```scala
jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv() 
```
Add the following to your `plugins.sbt`:
```scala
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
```
## Using `PlaywrightJSEnv` 
The [scala-js-env-playwright](https://github.com/gmkumar2005/scala-js-env-playwright) supports `ESModule` and `CommonJS` modules.
It enables automation of Chromium, Firefox, and WebKit browsers.
Add the following to your `build.sbt`:
```scala
jsEnv := new PWEnv(
        browserName = "chrome",
        headless = true,
        showLogs = false
    )
```
Add the following to your `plugins.sbt`:
```scala
libraryDependencies += "io.github.gmkumar2005" %% "scala-js-env-playwright" % "0.1.12"
```

## Writing test cases with  munit-cats-effect
In Calico, every component is represented as `Resource[IO, HtmlElement[IO]]`. 
Any Resource has the potential to form a Local Fixture in MUnit. 
Local fixtures are instantiated just once for the entirety of the test suite.

```scala
package basic

import calico.*
import calico.syntax.*
import calico.html.io.*
import calico.html.io.given
import cats.effect.IO
import cats.effect.Resource
import domutils.CalicoSuite
import fs2.dom.Element
import fs2.dom.Node
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import org.scalajs.dom
import org.scalajs.dom.document

class BasicSuite extends CatsEffectSuite {

  // Prepare the DOM for loading the application
  val appDiv: dom.Element = document.createElement("div")
  appDiv.id = "app"
  document.body.appendChild(appDiv)

  val rootElementId: String = "app"
  val window: Window[IO] = Window[IO]
  val rootElement: IO[Node[IO]] = window.document.getElementById(rootElementId).map(_.get)
  

  /**
   * The `mainApp` is a test fixture in munit. 
   * A fixture is a fixed state of a set of objects
   * used as a baseline for running tests. 
   * The purpose of a test fixture is to ensure that there is a well-known and 
   * fixed environment in which tests are run so that results are repeatable. 
   * The `mainApp` is an IOFixture which is a type of fixture provided by the
   * munit-cats-effect library. IOFixture is used for managing resources 
   * that have a lifecycle, such as opening and closing a database connection, 
   * or starting and stopping a server. 
   * The IOFixture is shared across all tests in the suite. 
   * It is created once and then passed to each test and cleaned up after all tests are run.
   */
  val mainApp: IOFixture[Node[IO]] = ResourceSuiteLocalFixture(
    "main-app",
    Resource.eval(rootElement)
  )

  override def munitFixtures = List(mainApp)

  test("renders empty elements") {
    val empty_div: Resource[IO, Element[IO]] = div("")
    empty_div.renderInto(mainApp()).surround {
      IO {
        val expectedEl = document.createElement("div")
        val actual = dom.document.querySelector("#app > div")
        assert(actual != null, "querySelector returned null check if the query is correct")
        assertEquals(actual.outerHTML, expectedEl.outerHTML)
      }
    } *> {
      val empty_span: Resource[IO, Element[IO]] = span("")
      empty_span.renderInto(mainApp()).surround {
        IO {
          val expectedEl = document.createElement("span")
          val actual = dom.document.querySelector("#app > span")
          assert(actual != null, "querySelector returned null. Check if the query is correct")
          assertEquals(actual.outerHTML, expectedEl.outerHTML)
        }
      }
    }
  }
}

```
The test case *`(renders empty elements)`* is designed to verify that the application correctly renders empty HTML elements. 
It does this by creating empty div and span elements, rendering them into the mainApp fixture, 
and then checking that the rendered elements match the expected output.  

### Here's a step-by-step breakdown:  

An empty div element is created using `div("")`. 

1. This is a `Resource[IO, Element[IO]]`, which means it's a resource that can be used and then cleaned up after use.  

2. The div element is rendered into the mainApp fixture using `empty_div.renderInto(mainApp())`. 
The `surround` method is then called to execute testing code before and after the resource is used.  

3. Inside the `surround` block, an IO effect is created. 
This effect creates an expected div element using `document.createElement("div")`, fetches the actual rendered element from the DOM 
using `dom.document.querySelector("#app > div")`, and then asserts that the actual element is not null and that its outer HTML matches the expected element's outer HTML.  
The same steps are repeated for an empty span element.  
This test case ensures that the application can correctly render empty div and span elements, and that the rendered elements are correctly added to the DOM.

## Writing test cases with scala-test
In Calico, every component is represented as `Resource[IO, HtmlElement[IO]]`. Scalatest requires `cats-effect-testing` to run test cases based on cats-effects.
Basic test cases can be written as follows:

```scala
package basic

import calico.*
import calico.html.io.*
import calico.html.io.given
import cats.Monad
import cats.effect.IO
import cats.effect.Resource
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.dom.Dom
import fs2.dom.Element
import fs2.dom.Node
import fs2.dom.Window
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers.equal
import org.scalatest.matchers.should.Matchers.should

class BasicSpec extends AsyncFunSuite with AsyncIOSpec {
  // Prepare the DOM
  val appDiv: dom.Element = document.createElement("div")
  appDiv.id = "app"
  document.body.appendChild(appDiv)

  val rootElementId: String = "app"
  val window: Window[IO] = Window[IO]
  val rootElement: IO[Node[IO]] = window.document.getElementById(rootElementId).map(_.get)
  def mainApp() = rootElement
  extension [F[_]](componentUnderTest: Resource[F, Node[F]])
    /**
     * Combines the component under test with a root element 
     * and mounts the component into the root element.
     */
    def mountInto(rootElement: F[Node[F]])(using Monad[F], Dom[F]): Resource[F, Unit] = {
      Resource
        .eval(rootElement)
        .flatMap(root =>
          componentUnderTest.flatMap(e =>
            Resource.make(root.appendChild(e))(_ => root.removeChild(e))))
    }

  test("renders empty elements") {
    val empty_div: Resource[IO, Element[IO]] = div("")
    empty_div.mountInto(mainApp()).surround {
      IO {
        val expectedEl = document.createElement("div")
        val actual = dom.document.querySelector("#app > div")
        assert(actual != null, "querySelector returned null. Check if the query is correct")
        actual.outerHTML should equal(expectedEl.outerHTML)
      }
    }
  }
}
```
The test case *`(renders empty elements)`* is designed to verify that the application correctly renders empty HTML elements.
It does this by creating empty div element, rendering them into the mainApp(), `mainApp()` is an alias for `rootElement`
and then checking that the rendered elements match the expected output.

### Here's a step-by-step breakdown:
An empty div element is created using `div("")`.

1. This is a `Resource[IO, Element[IO]]`, which means it's a resource that can be used and then cleaned up after use.

2. The div element is rendered into the mainApp()  fixture using `empty_div.mountInto(mainApp())`.
   The `surround` method is then called to execute testing code before and after the resource is used.

3. Inside the `surround` block, an IO effect is created.
   This effect creates an expected div element using `document.createElement("div")`, fetches the actual rendered element from the DOM
   using `dom.document.querySelector("#app > div")`, and then asserts that the actual element is not null and that its outer HTML matches the expected element's outer HTML.  
   This test case ensures that the application can correctly render empty div element, and that the rendered elements are correctly added to the DOM.
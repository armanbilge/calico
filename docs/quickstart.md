# Quick Start

* If you haven't mastered HTML yet, [now might be the time to do that](https://developer.mozilla.org/en-US/docs/Web/HTML).
* New to Scala.js? Try the [tutorials](https://www.scala-js.org/doc/tutorial/).
* New to Cats Effect? It has its own [Getting Started page](https://typelevel.org/cats-effect/docs/getting-started).
* Familiarity with [fs2](https://fs2.io/) will also be helpful.

## Step One: Create a new sbt project

One way to create a skeleton Scala.js sbt project is detailed at
[Getting Started with Scala.js and Vite](https://www.scala-js.org/doc/tutorial/scalajs-vite.html).
However some of the versions in that tutorial are older than what Calico needs.

An easier way to create a skeleton Calico project is with a Giter8 template.
Just run
```
sbt new tsnee/scalajs-calico.g8
```

## Step Two: Run the example

From the top level directory of the new project, run
```
npm install
npm run dev
```

Then navigate to the URL displayed by `npm run dev`, by default [http://localhost:5173/](http://localhost:5173/).
You will see the example described in [IOWebApp](iowebapp.md).

## Step Three: RTFM

Read [IOWebApp](iowebapp.md) if you haven't already. `src/main/scala/.../MyCalicoApp.scala` is the first example on that page.

Then read [Core Concepts](concepts.md).
As you read, try copying the code snippets into your project.
Run `sbt ~fastLinkJS` in another terminal to see your changes in real time.
For instance, put the interactive Hello World code into the same directory as `MyCalicoApp.scala` using the same
package. Edit `MyCalicoApp.scala` by replacing the `div` line with `component`.

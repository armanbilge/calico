# Calico

**Calico** is a UI library for the [Typelevel.js](https://typelevel.org/) ecosystem. It leverages the abstractions provided by [Cats Effect](https://typelevel.org/cats-effect/) and [FS2](https://fs2.io/) to provide a fluent DSL for building web applications that are composable, reactive, and safe. If you enjoy working with Cats Effect and FS2 then I hope that you will like **Calico** as well.

### Acknowledgements

**Calico** was inspired by [Laminar](https://Laminar.dev/). I have yet only had time to plagiarize the DSL and a few of the examples ;) Thanks to [@raquo](https://github.com/raquo/) for sharing their wisdom.

I am very grateful to [@SystemFw](https://github.com/SystemFw/) who gave me a tutorial on all things [Functional Reactive Programming](https://en.wikipedia.org/wiki/Functional_reactive_programming) shortly before I embarked on this project.

### Try it!

With special thanks to [@yurique], you can now try **Calico** right in your browser at [scribble.ninja](https://scribble.ninja/)!

```scala
libraryDependencies += "com.armanbilge" %%% "calico" % "@PRERELEASE_VERSION@"
```

Please open issues (and PRs!) for anything and everything :)

### Integrations

- The incredible [@yurique] has ported [frontroute](https://frontroute.dev/v/0.17.x-calico/) to Calico, including its extensive docs and demos!
- [calico-smithy4s-demo](https://github.com/kubukoz/calico-smithy4s-demo/) showcases a fullstack app and integration with [smithy4s](https://github.com/disneystreaming/smithy4s/). Thank you [@kubukoz](https://github.com/kubukoz/)!

[@yurique]: https://github.com/yurique/
ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlUntaggedAreSnapshots := false

ThisBuild / crossScalaVersions := Seq("3.1.2")
ThisBuild / scalacOptions ++= Seq("-new-syntax", "-indent", "-source:future")

lazy val root = tlCrossRootProject.aggregate(calico, widget, example, todoMvc)

lazy val calico = project
  .in(file("calico"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.7.0",
      "org.typelevel" %%% "cats-effect" % "3.3.11",
      "co.fs2" %%% "fs2-core" % "3.2.7",
      "org.typelevel" %%% "shapeless3-deriving" % "3.0.4",
      "dev.optics" %%% "monocle-core" % "3.1.0",
      "com.raquo" %%% "domtypes" % "0.16.0-RC2",
      "org.scala-js" %%% "scalajs-dom" % "2.1.0"
    )
  )

lazy val widget = project
  .in(file("widget"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico-widget"
  )
  .dependsOn(calico)

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .dependsOn(calico, widget)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("calico")))
    },
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-macro" % "3.1.0"
    )
  )

lazy val todoMvc = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .dependsOn(calico)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("todomvc")))
    },
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-macro" % "3.1.0"
    )
  )

lazy val jsdocs = project.dependsOn(calico, widget).enablePlugins(ScalaJSPlugin)
lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    mdocJS := Some(jsdocs),
    laikaConfig ~= { _.withRawContent },
    tlSiteHeliumConfig ~= {
      // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
      _.site.autoLinkJS()
    },
    tlSiteRelatedProjects ++= Seq(
      TypelevelProject.CatsEffect,
      TypelevelProject.Fs2,
      "http4s-dom" -> url("https://http4s.github.io/http4s-dom/")
    ),
    laikaInputs := {
      import laika.ast.Path.Root
      laikaInputs
        .value
        .delegate
        .addFile(
          (todoMvc / Compile / fullOptJS / artifactPath).value,
          Root / "todomvc" / "index.js")
    },
    mdocVariables += {
      val src = IO.readLines(
        (todoMvc / sourceDirectory).value / "main" / "scala" / "todomvc" / "TodoMvc.scala")
      "TODO_MVC_SRC" -> src.dropWhile(!_.startsWith("package")).mkString("\n")
    },
    laikaSite := laikaSite.dependsOn((todoMvc / Compile / fullOptJS)).value
  )

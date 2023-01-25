ThisBuild / tlBaseVersion := "0.2"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / startYear := Some(2022)
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)

ThisBuild / tlCiReleaseBranches ++= Seq("series/0.1")
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.2.1")
ThisBuild / scalacOptions ++= Seq("-new-syntax", "-indent", "-source:future")
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / tlJdkRelease := Some(8)

val CatsVersion = "2.9.0"
val CatsEffectVersion = "3.4.5"
val Fs2Version = "3.5.0"
val Fs2DomVersion = "0.2.0-M1"
val MonocleVersion = "3.2.0"

lazy val root =
  tlCrossRootProject.aggregate(frp, calico, router, sandbox, todoMvc, unidocs)

lazy val frp = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("frp"))
  .settings(
    name := "calico-frp",
    tlVersionIntroduced := Map("3" -> "0.1.1"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion,
      "co.fs2" %%% "fs2-core" % Fs2Version,
      "org.typelevel" %%% "cats-laws" % CatsVersion % Test,
      "org.typelevel" %%% "cats-effect-testkit" % CatsEffectVersion % Test,
      "org.typelevel" %%% "discipline-munit" % "1.0.9" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "0.7.29" % Test
    )
  )

lazy val generateDomDefs = taskKey[Seq[File]]("Generate SDT sources")

lazy val calico = project
  .in(file("calico"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico",
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "fs2-dom" % Fs2DomVersion,
      "org.typelevel" %%% "shapeless3-deriving" % "3.3.0",
      "dev.optics" %%% "monocle-core" % MonocleVersion,
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    ),
    Compile / generateDomDefs := {
      import _root_.calico.html.codegen.DomDefsGenerator
      import cats.effect.unsafe.implicits.global
      import sbt.util.CacheImplicits._
      (Compile / generateDomDefs).previous(sbt.fileJsonFormatter).getOrElse {
        DomDefsGenerator.generate((Compile / sourceManaged).value / "domdefs").unsafeRunSync()
      }
    },
    Compile / sourceGenerators += (Compile / generateDomDefs)
  )
  .dependsOn(frp.js)

lazy val router = project
  .in(file("router"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico-router",
    tlVersionIntroduced := Map("3" -> "0.1.2"),
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "fs2-dom" % Fs2DomVersion,
      "org.http4s" %%% "http4s-core" % "0.23.18"
    )
  )

lazy val sandbox = project
  .in(file("sandbox"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .dependsOn(calico, router)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("calico")))
    },
    libraryDependencies ++= Seq(
      "dev.optics" %%% "monocle-macro" % MonocleVersion
    )
  )

lazy val todoMvc = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSPlugin, BundleMonPlugin, NoPublishPlugin)
  .dependsOn(calico, router)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("todomvc")))
    },
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-jawn" % "0.14.3"
    ),
    bundleMonCheckRun := true,
    bundleMonCommitStatus := false,
    bundleMonPrComment := false
  )

ThisBuild / githubWorkflowBuild +=
  WorkflowStep.Sbt(
    List("bundleMon"),
    name = Some("Monitor artifact size"),
    cond = Some("matrix.project == 'rootJS'")
  )

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(ScalaJSPlugin, TypelevelUnidocPlugin)
  .settings(
    name := "calico-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(frp.js, calico, router)
  )

lazy val jsdocs = project.dependsOn(calico, router).enablePlugins(ScalaJSPlugin)
lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("calico"),
    mdocJS := Some(jsdocs),
    laikaConfig ~= { _.withRawContent },
    tlSiteHeliumConfig ~= {
      // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
      _.site.autoLinkJS()
    },
    tlSiteRelatedProjects ++= Seq(
      TypelevelProject.CatsEffect,
      TypelevelProject.Fs2,
      "fs2-dom" -> url("https://github.com/armanbilge/fs2-dom/"),
      "http4s-dom" -> url("https://http4s.github.io/http4s-dom/")
    ),
    laikaInputs := {
      import laika.ast.Path.Root
      val jsArtifact = (todoMvc / Compile / fullOptJS / artifactPath).value
      val sourcemap = jsArtifact.getName + ".map"
      laikaInputs
        .value
        .delegate
        .addFile(
          jsArtifact,
          Root / "todomvc" / "index.js"
        )
        .addFile(
          jsArtifact.toPath.resolveSibling(sourcemap).toFile,
          Root / "todomvc" / sourcemap
        )
    },
    mdocVariables += {
      val src = IO.readLines(
        (todoMvc / sourceDirectory).value / "main" / "scala" / "todomvc" / "TodoMvc.scala")
      "TODO_MVC_SRC" -> src.dropWhile(!_.startsWith("package")).mkString("\n")
    },
    laikaSite := laikaSite.dependsOn(todoMvc / Compile / fullOptJS).value
  )

import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.core.DirectMissingMethodProblem

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

ThisBuild / crossScalaVersions := Seq("3.3.1")
ThisBuild / scalacOptions ++= Seq("-new-syntax", "-indent", "-source:future")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))

val CatsVersion = "2.10.0"
val CatsEffectVersion = "3.5.3"
val Fs2Version = "3.9.4"
val Fs2DomVersion = "0.2.1"
val Http4sVersion = "0.23.25"
val Http4sDomVersion = "0.2.11"
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
      "org.typelevel" %%% "discipline-munit" % "2.0.0-M3" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M4" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0-M11" % Test
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
      "org.typelevel" %%% "shapeless3-deriving" % "3.4.1",
      "dev.optics" %%% "monocle-core" % MonocleVersion,
      "org.scala-js" %%% "scalajs-dom" % "2.8.0"
    ),
    Compile / generateDomDefs := {
      import _root_.calico.html.codegen.DomDefsGenerator
      import cats.effect.unsafe.implicits.global
      import sbt.util.CacheImplicits._
      (Compile / generateDomDefs).previous(sbt.fileJsonFormatter).getOrElse {
        DomDefsGenerator.generate((Compile / sourceManaged).value / "domdefs").unsafeRunSync()
      }
    },
    Compile / sourceGenerators += (Compile / generateDomDefs),
    mimaBinaryIssueFilters ++= Seq(
      // Static forwarder, only used in Java interop
      ProblemFilters.exclude[DirectMissingMethodProblem]("calico.html.EventProp.apply")
    )
  )
  .dependsOn(frp.js)

lazy val router = project
  .in(file("router"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico-router",
    tlVersionIntroduced := Map("3" -> "0.1.2"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion,
      "com.armanbilge" %%% "fs2-dom" % Fs2DomVersion,
      "org.http4s" %%% "http4s-core" % Http4sVersion
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
      "org.http4s" %%% "http4s-dom" % Http4sDomVersion,
      "dev.optics" %%% "monocle-macro" % MonocleVersion
    ),
    scalacOptions ~= (_.filterNot(_.startsWith("-Wunused")))
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
      "io.circe" %%% "circe-jawn" % "0.14.6"
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
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(frp.js, calico, router),
    ScalaUnidoc / unidoc / fullClasspath := (todoMvc / Compile / fullClasspath).value
  )

lazy val jsdocs = project
  .settings(
    scalacOptions ~= (_.filterNot(_.startsWith("-Wunused"))),
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-dom" % Http4sDomVersion,
      "org.http4s" %%% "http4s-circe" % Http4sVersion
    )
  )
  .dependsOn(calico, router)
  .enablePlugins(ScalaJSPlugin)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("calico"),
    tlSiteIsTypelevelProject := Some(TypelevelProject.Affiliate),
    mdocJS := Some(jsdocs),
    laikaConfig ~= { _.withRawContent },
    tlSiteHelium ~= {
      import laika.helium.config._
      _.site.mainNavigation(appendLinks = Seq(
        ThemeNavigationSection(
          "Related Projects",
          TextLink.external("https://typelevel.org/cats-effect/", "Cats Effect"),
          TextLink.external("https://fs2.io/", "FS2"),
          TextLink.external("https://github.com/armanbilge/fs2-dom/", "fs2-dom"),
          TextLink.external("https://http4s.github.io/http4s-dom/", "http4s-dom")
        )
      ))
    },
    laikaInputs := {
      import laika.ast.Path.Root
      import laika.io.model.FilePath
      val jsArtifact = (todoMvc / Compile / fullOptJS / artifactPath).value
      val sourcemap = jsArtifact.getName + ".map"
      laikaInputs
        .value
        .delegate
        .addFile(
          FilePath.fromJavaFile(jsArtifact),
          Root / "todomvc" / "index.js"
        )
        .addFile(
          FilePath.fromNioPath(jsArtifact.toPath.resolveSibling(sourcemap)),
          Root / "todomvc" / sourcemap
        )
    },
    mdocVariables += ("HTTP4S_DOM_VERSION" -> Http4sDomVersion),
    mdocVariables += {
      val src = IO.readLines(
        (todoMvc / sourceDirectory).value / "main" / "scala" / "todomvc" / "TodoMvc.scala")
      "TODO_MVC_SRC" -> src.dropWhile(!_.startsWith("package")).mkString("\n")
    },
    laikaSite := laikaSite.dependsOn(todoMvc / Compile / fullOptJS).value
  )

ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlUntaggedAreSnapshots := false

ThisBuild / crossScalaVersions := Seq("3.1.1")
ThisBuild / scalacOptions ++= Seq("-new-syntax", "-indent", "-source:future")

lazy val root = tlCrossRootProject.aggregate(calico, example)

lazy val calico = project
  .in(file("calico"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.7.0",
      "org.typelevel" %%% "cats-effect" % "3.3.7",
      "co.fs2" %%% "fs2-core" % "3.2.5",
      "org.typelevel" %%% "shapeless3-deriving" % "3.0.4",
      "com.raquo" %%% "domtypes" % "0.16.0-RC2",
      "org.scala-js" %%% "scalajs-dom" % "2.1.0"
    )
  )

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
  .dependsOn(calico)
  .settings(
    scalaJSUseMainModuleInitializer := true
  )

lazy val jsdocs = project.dependsOn(calico).enablePlugins(ScalaJSPlugin)
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
    )
  )

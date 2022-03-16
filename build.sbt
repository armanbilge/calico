ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)

ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.1.1")

lazy val root = tlCrossRootProject.aggregate(calico)

lazy val calico = project
  .in(file("calico"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calico",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.7.0",
      "org.typelevel" %%% "cats-effect" % "3.3.7",
      "co.fs2" %%% "fs2-core" % "3.2.5",
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

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      BuildInfoKey.action("scalaDomTypesVersion")("0.16.0-SNAPSHOT")
    ),
    buildInfoPackage := "metaProject",
    // Compile-time dependencies
    libraryDependencies ++= Seq(
      "com.raquo" %% "domtypes" % "17.0.0-M1",
      "org.scalameta" %% "scalafmt-dynamic" % "3.6.1"
    )
  )

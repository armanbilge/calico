resolvers +=
  "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

val sbtTypelevelVersion = "0.4.6-30-3ab52fb-SNAPSHOT"
addSbtPlugin("org.typelevel" % "sbt-typelevel" % sbtTypelevelVersion)
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % sbtTypelevelVersion)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.9.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.1")

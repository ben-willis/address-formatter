def publishVersion: String = sys.env.getOrElse("RELEASE_VERSION", "local-SNAPSHOT")
def isRelease: Boolean     = publishVersion != "local-SNAPSHOT"

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.1.2")

name := "address-formatter"
organization := "io.github.ben-willis"
version := publishVersion
scalaVersion := "2.12.13"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/ben-willis/address-formatter"),
    "scm:git@github.com:ben-willis/address-formatter.git"
  )
)

ThisBuild / description := "Universal international address formatter in Scala."
ThisBuild / licenses := List("MIT" -> new URL("https://github.com/ben-willis/address-formatter/blob/main/LICENSE"))
ThisBuild / homepage := Some(url("https://github.com/ben-willis/address-formatter"))

ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isRelease) Some("releases" at nexus + "service/local/staging/deploy/maven2")
  else Some("snapshots" at nexus + "content/repositories/snapshots")
}
ThisBuild / publishMavenStyle := true

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"    % "0.12.3",
  "io.circe" %% "circe-generic" % "0.12.3",
  "io.circe" %% "circe-parser"  % "0.12.3",
  "io.circe" %% "circe-yaml"    % "0.12.0"
)
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.7"
).map(_ % Test)

Compile / unmanagedResourceDirectories += baseDirectory.value / "address-formatting/conf"
Test / unmanagedResourceDirectories += baseDirectory.value / "address-formatting/testcases"

scalacOptions += "-Ypartial-unification"

name := "address-formatter"
organization := "io.github.ben-willis"
version := "0.1.0"
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
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
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

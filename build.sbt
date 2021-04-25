name := "address-formatter"
organization := "io.github.ben-willis"
version := "1.0-SNAPSHOT"
scalaVersion := "2.12.13"

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "io.circe" %% "circe-yaml" % "0.12.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.7" % "test"

Compile / unmanagedResourceDirectories += baseDirectory.value / "address-formatting/conf"
Test / unmanagedResourceDirectories += baseDirectory.value / "address-formatting/testcases"

scalacOptions += "-Ypartial-unification"

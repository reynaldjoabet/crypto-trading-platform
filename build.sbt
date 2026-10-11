import Dependencies.*

ThisBuild / scalaVersion       := "3.8.0"
ThisBuild / crossScalaVersions := Seq("3.3.8", "3.9.0")
ThisBuild / organization       := "io.trading"
ThisBuild / version            := "0.1.0-SNAPSHOT"
ThisBuild / scalafmtOnCompile  := false
//ThisBuild / Test / fork := true
//ThisBuild / Test / parallelExecution := false

ThisBuild / scalacOptions := {
  val base = Seq(
    "-encoding",
    "UTF-8",
    "-no-indent",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-java-output-version:17",
    "-Werror",
    "-Wvalue-discard",
    "-Wnonunit-statement",
    "-Xcheck-macros",
    "-Xmax-inlines:64"
  )
  if (scalaVersion.value.startsWith("3.3"))
    // -source:3.3 gates syntax to the 3.3-compatible subset
    // -Xlint:all is the 3.3 lint umbrella (renamed to -Wshadow:all in 3.4+)
    base ++ Seq("-source:3.3", "-Xlint:all")
  else
    // -Xlint is deprecated/removed post-3.3; -Wshadow:all is the replacement for shadowing lints
    base :+ "-Wshadow:all"
}

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name                 := "crypto-trading-platform",
    libraryDependencies ++= all,
    testFrameworks       += new TestFramework("munit.Framework"),
    Compile / mainClass  := Some("trading.app.Main"),
    dockerBaseImage      := "eclipse-temurin:21-jre",
    dockerExposedPorts  ++= Seq(8080, 8081)
  )

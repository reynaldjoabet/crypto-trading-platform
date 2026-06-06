import Dependencies.*

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "io.trading"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalafmtOnCompile := false
//ThisBuild / Test / fork := true
//ThisBuild / Test / parallelExecution := false

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-explain", // + actionable error messages
  "-source:3.3", // + pin source level, no silent drift
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-language:strictEquality", // + catch nonsensical == (Money vs String, etc.)
  "-Ykind-projector",
  "-Xmax-inlines",
  "64"
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name := "crypto-trading-platform",
    libraryDependencies ++= all,
    testFrameworks += new TestFramework("munit.Framework"),
    Compile / mainClass := Some("trading.app.Main"),
    dockerBaseImage := "eclipse-temurin:21-jre",
    dockerExposedPorts ++= Seq(8080, 8081)
  )

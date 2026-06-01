import Dependencies.*

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "io.trading"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalafmtOnCompile := false
ThisBuild / Test / fork := true
ThisBuild / Test / parallelExecution := false

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
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

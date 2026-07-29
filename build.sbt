import Dependencies.*

ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "io.trading"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalafmtOnCompile := false
//ThisBuild / Test / fork := true
//ThisBuild / Test / parallelExecution := false

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-source:3.3",
  "-java-output-version:17",
  "-Werror",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Xlint:all",
  "-Ysafe-init",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

Global / onChangedBuildSource := ReloadOnSourceChanges

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

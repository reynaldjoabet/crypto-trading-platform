import Dependencies.*

ThisBuild / scalaVersion      := "3.9.0"
ThisBuild / organization      := "io.trading"
ThisBuild / version           := "0.1.0-SNAPSHOT"
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
  "-java-output-version:17",
  "-Werror",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Xcheck-macros",
  "-Xmax-inlines:64",
  "-Wshadow:all"
)

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

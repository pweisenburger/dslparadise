import sbt._
import Keys._

object MyBuild extends Build {
  lazy val dslparadiseCompilation = Project(
    id = "dslparadise-compilation",
    base = file(".")
  ) settings (
    publishArtifact := false,
    run := {
      (run in dslparadiseSandbox in Compile).evaluated
    }
  ) aggregate (
    dslparadise,
    dslparadiseTypes
  )

  lazy val dslparadise = Project(
    id = "dslparadise",
    base = file("plugin")
  ) settings (
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    )
  )

  lazy val dslparadiseTypes = Project(
    id = "dslparadise-types",
    base = file("library")
  )

  lazy val dslparadiseSandbox = Project(
    id = "dslparadise-sandbox",
    base = file("sandbox")
  ) settings (
    publishArtifact := false,
    scalacOptions += "-Xplugin:" + (packageBin in Compile in dslparadise).value
  ) dependsOn (
    dslparadiseTypes,
    dslparadise
  )
}

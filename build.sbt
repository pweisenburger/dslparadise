scalaVersion in ThisBuild := "2.12.2"

crossScalaVersions in ThisBuild := Seq("2.11.6", "2.11.7", "2.11.8", "2.11.9", "2.11.10", "2.11.11", "2.12.0", "2.12.1", "2.12.2")

version in ThisBuild := "0.0.1-SNAPSHOT"

organization in ThisBuild := "dslparadise"

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint")


def preventPublication(project: Project) = project settings (
  publish := { },
  publishLocal := { },
  publishArtifact := false,
  packagedArtifacts := Map.empty)


lazy val dslparadiseCompilation = preventPublication(project
  in file(".")
  settings (
    normalizedName := "dslparadise-compilation",
    run := { (run in dslparadiseSandbox in Compile).evaluated }
  )
  aggregate (dslparadise, dslparadiseTypes))

lazy val dslparadise = (project
  in file("plugin")
  settings (
    normalizedName := "dslparadise",
    crossVersion := CrossVersion.full,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value))

lazy val dslparadiseTypes = (project
  in file("library")
  settings (
    normalizedName := "dslparadise-types"))

lazy val dslparadiseSandbox = preventPublication(project
  in file("sandbox")
  settings (
    normalizedName := "dslparadise-sandbox",
    scalacOptions += "-Xplugin:" + (packageBin in Compile in dslparadise).value
  )
  dependsOn (dslparadise, dslparadiseTypes))

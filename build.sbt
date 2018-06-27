enablePlugins(GitVersioning)

git.useGitDescribe in ThisBuild := true

scalaVersion in ThisBuild := "2.12.6"

crossScalaVersions in ThisBuild := Seq(
  "2.11.6", "2.11.7", "2.11.8", "2.11.9", "2.11.10", "2.11.11", "2.11.12",
  "2.12.0", "2.12.1", "2.12.2", "2.12.3", "2.12.4", "2.12.5", "2.12.6")

organization in ThisBuild := "de.tuda.stg"

licenses in ThisBuild += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint")


lazy val dslparadiseCompilation = (project
  in file(".")
  settings (
    skip in publish := true,
    normalizedName := "dslparadise-compilation",
    run := { (run in dslparadiseSandbox in Compile).evaluated }
  )
  aggregate (dslparadise, dslparadiseTypes))

lazy val dslparadise = (project
  in file("plugin")
  settings (
    normalizedName := "dslparadise",
    crossVersion := CrossVersion.patch,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value))

lazy val dslparadiseTypes = (project
  in file("library")
  settings (
    normalizedName := "dslparadise-types"))

lazy val dslparadiseSandbox = (project
  in file("sandbox")
  settings (
    skip in publish := true,
    normalizedName := "dslparadise-sandbox",
    scalacOptions += "-Xplugin:" + (packageBin in Compile in dslparadise).value
  )
  dependsOn (dslparadise, dslparadiseTypes))

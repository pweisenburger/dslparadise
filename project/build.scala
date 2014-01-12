import sbt._
import Keys._

object build extends Build {
  lazy val sharedSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.11.0-SNAPSHOT",
    crossVersion := CrossVersion.full,
    version := "0.0.1-SNAPSHOT",
    organization := "org.dslparadise",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases")
    ),
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    scalacOptions ++= Seq("-deprecation", "-feature"),
    parallelExecution in Test := false, // hello, reflection sync!!
    logBuffered := false
  )

  lazy val plugin = Project(
    id   = "dslparadise",
    base = file("plugin")
  ) settings (
    sharedSettings : _*
  ) settings (
    resourceDirectory in Compile := baseDirectory.value / "src" / "main" / "scala" / "org" / "dslparadise" / "embedded",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-library" % scalaVersion.value,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    )
    // TODO: how to I make this recursion work?
    // run <<= run in Compile in sandbox,
    // test <<= test in Test in tests
  )

  lazy val usePluginSettings = Seq(
    scalacOptions in Compile <++= (Keys.`package` in (plugin, Compile)) map { (jar: File) =>
      System.setProperty("dslparadise.plugin.jar", jar.getAbsolutePath)
      val addPlugin = "-Xplugin:" + jar.getAbsolutePath
      // Thanks Jason for this cool idea (taken from https://github.com/retronym/boxer)
      // add plugin timestamp to compiler options to trigger recompile of
      // main after editing the plugin. (Otherwise a 'clean' is needed.)
      val dummy = "-Jdummy=" + jar.lastModified
      Seq(addPlugin, dummy)
    }
  )

  lazy val sandbox = Project(
    id   = "sandbox",
    base = file("sandbox")
  ) settings (
    sharedSettings ++ usePluginSettings: _*
  )

  lazy val tests = Project(
    id   = "tests",
    base = file("tests")
  ) settings (
    sharedSettings ++ usePluginSettings: _*
  ) settings (
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
    // TODO: dunno what are the versions of scalatest and scalacheck for 2.11.0-SNAPSHOT
    // libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    // libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
    // scalacOptions ++= Seq("-Xprint:typer")
    // scalacOptions ++= Seq("-Xlog-implicits")
  )
}

package dslparadise

import scala.tools.nsc.{Global, SubComponent}
import scala.tools.nsc.plugins.{Plugin => NscPlugin}
import scala.collection.mutable.{Map, Set}
import dslparadise.typechecker.Analyzer

class Plugin(val global: Global) extends NscPlugin {
  val name = "dslparadise"
  val description = "DSL Paradise"
  val components = List.empty

  // The initialization process (i.e. rewiring phase factories to modify typer
  // functionality) is based on "Macro Paradise" code from the Scala 2.10 branch.
  // Scala 2.11 introduced new complier plugin capabilities which simplified
  // "Macro Paradise" code but cannot be employed for our use case.

  val phasesDescMap = classOf[Global]
    .getDeclaredMethod("phasesDescMap")
    .invoke(global)
    .asInstanceOf[Map[SubComponent, String]]

  // Replace Global.analyzer to customize namer and typer (step 1 of 3)
  //
  // Unfortunately compiler plugins are instantiated too late. Therefore by now
  // analyzer has already been used to instantiate the "namer", "packageobjects"
  // and "typer" subcomponents. These are not phases yet -- they are just phase
  // factories -- so no disaster yet, but we have to be quick. This warrants the
  // second step in this customization - rewiring phase factories.
  val analyzer = new { val global = Plugin.this.global } with Analyzer
  val analyzerField = classOf[Global].getDeclaredField("analyzer")
  analyzerField.setAccessible(true)
  analyzerField.set(global, analyzer)

  // Replace Global.analyzer to customize namer and typer (step 2 of 3)
  //
  // Luckily for us compiler plugins are instantiated quite early. So by now,
  // internal phases have only left a trace in "phasesSet" and in
  // "phasesDescMap". Also, up until now no one has really used the standard
  // analyzer, so we're almost all set except for the standard
  // `object typer extends analyzer.Typer(<some default context>)`
  // that is a member of `Global` and hence has been pre-initialized now.
  // Good news is that it's only used in later phases or as a host for less
  // important activities (error reporting, printing, etc).
  val phasesSetMapGetter = classOf[Global].getDeclaredMethod("phasesSet")
  val phasesSet = phasesSetMapGetter.invoke(global).asInstanceOf[Set[SubComponent]]
  // `scalac -help` doesn't instantiate standard phases
  if (phasesSet exists { _.phaseName == "typer" }) {
    def subComponentByName(name: String) =
      (phasesSet find { _.phaseName == name }).head
    def hijackDescription(pt: SubComponent, sc: SubComponent) =
      phasesDescMap(sc) = phasesDescMap(pt) + " in dslparadise"

    val oldScs @ List(oldNamer, oldPackageobjects, oldTyper) = List(
      subComponentByName("namer"), subComponentByName("packageobjects"), subComponentByName("typer"))
    val newScs = List(
      analyzer.namerFactory, analyzer.packageObjects, analyzer.typerFactory)
    oldScs zip newScs foreach { case (pt, sc) => hijackDescription(pt, sc) }
    phasesSet --= oldScs
    phasesSet ++= newScs
  }

  // Replace Global.analyzer to customize namer and typer (step 3 of 3)
  //
  // Now let's take a look at what we couldn't replace during steps 1 and 2.
  // here's what gets printed if we add the following line 
  //   `if (!getClass.getName.startsWith("org.dslparadise")) println(getClass.getName)`
  // to the standard namer and typer classes
  //
  //    scala.tools.nsc.Global$typer$
  //    scala.tools.nsc.typechecker.Implicits$ImplicitSearch
  //    ...
  //    scala.tools.nsc.transform.Erasure$Eraser
  //    ...
  //    scala.tools.nsc.typechecker.Namers$NormalNamer
  //    scala.tools.nsc.transform.Erasure$Eraser
  //    scala.tools.nsc.transform.Erasure$Eraser
  //    scala.tools.nsc.typechecker.Namers$NormalNamer
  //    ...
  //
  // Duh, we're still not done. But the good news is that it doesn't matter for
  // DSL paradise:
  // 1) ImplicitSearch is easily worked around by overriding `inferImplicit` and
  //    `allViewsFrom`
  // 2) `scala.tools.nsc.Global$typer$` is only used in later phases or as a
  //    host for less important activities (error reporting, printing, etc)
  // 3) Custom erasure typer and namers it spawns are also only used in later
  //    phases
  //
  // TODO: Theoretically, points 2 and 3 can still be customizable.
  // `Global.typer` can have itself set as a specialized subclass of
  // `scala.tools.nsc.Global$typer$` (it's possible because by now it's still
  // null, as object initialization is lazy). Erasure can be hijacked in the
  // same way as we hijack "namer", "packageobjects" and "typer". However,
  // for now this is all not essential, so I'm moving on
}

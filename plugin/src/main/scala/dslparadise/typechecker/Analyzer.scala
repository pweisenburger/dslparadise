package dslparadise
package typechecker

import scala.tools.nsc.typechecker.{Analyzer => NscAnalyzer}

trait Analyzer extends NscAnalyzer with Typers {
  override def newTyper(context: Context) =
    new Typer(context) with ParadiseTyper
}

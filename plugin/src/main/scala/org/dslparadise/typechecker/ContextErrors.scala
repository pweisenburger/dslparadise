package org.dslparadise
package typechecker

trait ContextErrors {
  self: Analyzer =>

  import global._
  import ErrorUtils._

  trait ParadiseTyperContextErrors extends TyperContextErrors {
    self: Typer =>

    import infer.setError

    object ParadiseTyperErrorGen {
      implicit val contextTyperErrorGen: Context = infer.getContext
    }
  }
}

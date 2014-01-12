package org.dslparadise
package typechecker

trait Typers {
  self: Analyzer =>

  import global._
  import definitions._
  import scala.reflect.internal.Mode

  trait ParadiseTyper extends Typer with ParadiseTyperContextErrors {
    import TyperErrorGen._
    import ParadiseTyperErrorGen._
    import infer._

    override def typedArgsForFormals(args: List[Tree], formals: List[Type], mode: Mode): List[Tree] = {
      println("hello world")
      super.typedArgsForFormals(args, formals, mode)
    }
  }
}

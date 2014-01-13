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
      val Impl = typeOf[org.dslparadise.annotations.Implicit]
      val Impo = typeOf[org.dslparadise.annotations.Import]
      val desugared = (args zip formals) map {
        case (tree, formal @ TypeRef(_, _, List(AnnotatedType(List(AnnotationInfo(Impl, _, _)), left, _), right))) ⇒
          silent(_.typed(tree.duplicate)) match {
            case SilentResultValue(result) =>
              result
            case SilentTypeError(_) =>
              val fixed = q"{ implicit u: $left ⇒ $tree }"
              val fixedTpe = typed(fixed).tpe
              println(tree, fixed, fixedTpe)
              fixed
          }
        case (tree, tp) ⇒ tree
      }
      println(s"desugared $args to $desugared")
      super.typedArgsForFormals(desugared, formals, mode)
    }
  }
}

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
        // TODO: expand typeref to Function1
        case (tree, formal @ TypeRef(_, _, List(AnnotatedType(List(AnnotationInfo(Impl, _, _)), left), right))) ⇒
          q"{ implicit u: $left ⇒ $tree }"

        case (tree, tp) ⇒
          tree
      }
      super.typedArgsForFormals(desugared, formals, mode)
    }
  }
}

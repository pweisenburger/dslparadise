package dslparadise
package typechecker

import scala.reflect.NameTransformer
import scala.reflect.internal.Mode

trait Typers extends ReportingSilencer {
  self: Analyzer =>

  import global._

  trait ParadiseTyper extends Typer with Silencer {
    def makeArg(name: TermName) =
      ValDef(Modifiers(Flag.PARAM), name, TypeTree(), EmptyTree)

    def makeImplicitArg(name: TermName) =
      ValDef(Modifiers(Flag.PARAM | Flag.IMPLICIT), name, TypeTree(), EmptyTree)

    case class Rewriting(
        val hasArgument: Boolean, val hasImplicitArgument: Boolean)(
        rewrite: (TermName, Tree, Type) => Tree) {
      def apply(name: TermName, tree: Tree, tpe: Type) = rewrite(name, tree, tpe)
    }

    // rewriting rules for the DSL Paradise types
    val rewritings = Map(
      "dslparadise.implicit =>" -> Rewriting(true, true) {
        (name, tree, tpe) => q"{ ${makeImplicitArg(name)} => $tree }"
      },

      "dslparadise.implicit import =>" -> Rewriting(true, true) {
        (name, tree, tpe) => q"{ ${makeImplicitArg(name)} => import $name._; $tree }"
      },

      "dslparadise.import =>" -> Rewriting(true, false) {
        (name, tree, tpe) => q"{ ${makeArg(name)} => import $name._; $tree }"
      },

      "dslparadise.import" -> Rewriting(false, false) {
        (name, tree, tpe) => q"{ import ${tpe.typeArgs(1).typeSymbol.companionSymbol}._; $tree }"
      }
    )

    // name for implicit argument
    val argumentName = "dslparadise.argument name"

    override def typed(tree: Tree, mode: Mode, pt: Type): Tree = {
      val newtree = pt match {
        case TypeRef(_, sym, args @ Seq(_, _)) =>
          // extract name for implicit argument if given
          val (symbol, name) =
            if ((NameTransformer decode sym.fullName) == argumentName)
              args match {
                case Seq(
                    TypeRef(_, sym, Seq(_, _)),
                    RefinedType(List(definitions.AnyRefTpe), Scope(decl))) =>
                  (sym, decl.name.toTermName)
                case _ =>
                  (NoSymbol, TermName(""))
              }
            else
              (sym, context.unit freshTermName "imparg$")

          // find rewriting rule for the expected type
          rewritings get (NameTransformer decode symbol.fullName) map { rewrite =>

            // do not rewrite tree if it does not introduce a new function
            // argument (i.e., the type remains unchanged) and the expression
            // compiles in its current form (this prevents infinite recursion)
            val keepTree = !rewrite.hasArgument && (silenceReporting {
              context inSilentMode {
                super.typed(tree.duplicate, mode, pt)
                !context.reporter.hasErrors
              }
            })

            if (!keepTree) {
              // apply rewriting rule
              val newtree = rewrite(name, tree, pt)

              // only rewrite tree if the rewriting does not result
              // in compiler errors
              val rewriteTree = silenceReporting {
                context inSilentMode {
                  super.typed(newtree.duplicate, mode, pt)
                  !context.reporter.hasErrors
                }
              }

              if (rewriteTree) newtree else tree
            }
            else
              tree

          } getOrElse tree

        case _ =>
          tree
      }

      super.typed(newtree, mode, pt)
    }
  }
}

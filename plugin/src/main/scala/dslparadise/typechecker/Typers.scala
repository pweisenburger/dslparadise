package dslparadise
package typechecker

import scala.reflect.NameTransformer
import scala.reflect.internal.Mode

trait Typers {
  self: Analyzer =>

  import global._

  trait ParadiseTyper extends Typer with TyperContextErrors {
    // rewriting rules for the DSL Paradise types
    val rewritings = Map(
      "dslparadise.implicit =>" -> { (arg: Tree, pt: Type) =>
        q"{ implicit $$bang => $arg }"
      },

      "dslparadise.implicit import =>" -> { (arg: Tree, pt: Type) =>
        q"{ implicit $$bang => import $$bang._; $arg }"
      },

      "dslparadise.import =>" -> { (arg: Tree, pt: Type) =>
        q"{ $$bang => import $$bang._; $arg }"
      },

      "dslparadise.import" -> { (arg: Tree, pt: Type) =>
        q"{ import ${pt.typeArgs(1).typeSymbol.companionSymbol}._; $arg }"
      }
    )

    override def typedArg(arg: Tree, mode: Mode, newmode: Mode, pt: Type): Tree = {
      val newarg = pt match {
        case TypeRef(_, sym, _) =>
          // find rewriting rule for the expected type
          rewritings get (NameTransformer decode sym.fullName) map { rewrite =>

            // only rewrite argument if it does not compile in its current form
            val rewriteArg = context inSilentMode {
              super.typedArg(arg.duplicate, mode, newmode, pt)
              context.reporter.hasErrors
            }

            if (rewriteArg) {
              // apply rewriting rule
              val newarg = rewrite(arg, pt)

              // to improve compiler-issued error messages, keep the original
              // (non-rewritten) argument if the new (rewritten) argument
              // produces compile errors
              // - that have no corresponding position in the source file
              //   (i.e. the position is within the code that was introduced
              //   by the rewriting) or
              // - whose message is "missing parameter type", which could be
              //   misleading if the rewriting introduced a function and the
              //   original code already had function type
              val keepArg = context inSilentMode {
                super.typedArg(newarg.duplicate, mode, newmode, pt)
                context.reporter.errors exists { e =>
                  e.errPos == NoPosition || e.errMsg == "missing parameter type"
                }
              }

              if (keepArg) arg else newarg
            }
            else
              arg

          } getOrElse arg

        case _ =>
          arg
      }

      super.typedArg(newarg, mode, newmode, pt)
    }
  }
}

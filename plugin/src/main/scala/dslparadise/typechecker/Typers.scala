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

    // rewriting rules for the DSL Paradise types
    val rewritings = Map(
      "dslparadise.implicit =>" -> { (name: TermName, arg: Tree, pt: Type) =>
        q"{ ${makeImplicitArg(name)} => $arg }"
      },

      "dslparadise.implicit import =>" -> { (name: TermName, arg: Tree, pt: Type) =>
        q"{ ${makeImplicitArg(name)} => import $name._; $arg }"
      },

      "dslparadise.import =>" -> { (name: TermName, arg: Tree, pt: Type) =>
        q"{ ${makeArg(name)} => import $name._; $arg }"
      },

      "dslparadise.import" -> { (name: TermName, arg: Tree, pt: Type) =>
        q"{ import ${pt.typeArgs(1).typeSymbol.companionSymbol}._; $arg }"
      }
    )

    // name for implicit argument
    val argumentName = "dslparadise.argument name"

    override def typedArg(arg: Tree, mode: Mode, newmode: Mode, pt: Type): Tree = {
      val newarg = pt match {
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

            // only rewrite argument if it does not compile in its current form
            val rewriteArg = silenceReporting {
              context inSilentMode {
                super.typedArg(arg.duplicate, mode, newmode, pt)
                context.reporter.hasErrors
              }
            }

            if (rewriteArg) {
              // apply rewriting rule
              val newarg = rewrite(name, arg, pt)

              // to improve compiler-issued error messages, keep the original
              // (non-rewritten) argument if the new (rewritten) argument
              // produces compile errors
              // - that have no corresponding position in the source file
              //   (i.e. the position is within the code that was introduced
              //   by the rewriting) or
              // - whose message is "missing parameter type", which could be
              //   misleading if the rewriting introduced a function and the
              //   original code already had function type
              val keepArg = silenceReporting {
                context inSilentMode {
                  super.typedArg(newarg.duplicate, mode, newmode, pt)
                  context.reporter.errors exists { e =>
                    e.errPos == NoPosition || e.errMsg == "missing parameter type"
                  }
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

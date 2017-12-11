package dslparadise
package typechecker

import scala.reflect.NameTransformer

trait Typers extends CalleeProcessor with ReportingSilencer {
  self: Analyzer =>

  import global._

  trait ParadiseTyper extends CalleeTyper with Silencer {
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
        (name, tree, tpe) => q"{ import ${tpe.typeSymbol.companionSymbol}._; $tree }"
      }
    )

    // name for implicit argument
    val argumentName = "dslparadise.argument name"

    // decompose a potential DSL Paradise type into its rewriting rule,
    // its argument and result type and its argument name
    def decomposeRewritingType(tpe: Type): Option[(Rewriting, Type, Type, TermName)] = {
      // extract name for implicit argument if given or create a fresh one
      val (symbol, argType, argName) =
        tpe match {
          // there is an explicit argument name
          case TypeRef(_, sym, args @ Seq(_, _))
              if (NameTransformer decode sym.fullName) == argumentName =>

            args match {
              case Seq(
                  tpe @ TypeRef(_, sym, Seq(_, _)),
                  RefinedType(Seq(definitions.AnyRefTpe), Scope(decl))) =>
                (sym, tpe, decl.name.toTermName)
              case _ =>
                (NoSymbol, NoType, nme.EMPTY)
            }

          // there is no explicit argument name
          case tpe @ TypeRef(_, sym, _) =>
            (sym, tpe, context.unit freshTermName "imparg$")

          // no type we can interpret
          case _ =>
            (NoSymbol, NoType, nme.EMPTY)
        }

      // find rewriting rule for the given type
      val rewriting = rewritings get (NameTransformer decode symbol.fullName)

      // extract argument and result types
      (rewriting, argType) match {
        case (Some(rewriting), TypeRef(_, _, Seq(argType, resultType))) =>
          Some((rewriting, argType, resultType, argName))
        case _ =>
          None
      }
    }
  }
}

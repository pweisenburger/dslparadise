package dslparadise
package typechecker

import scala.reflect.internal.Mode

trait CalleeProcessor {
  self: Analyzer with Typers =>

  import global._

  trait CalleeTyper extends Typer {
    self: ParadiseTyper =>

    override def typed(tree: Tree, mode: Mode, pt: Type): Tree = {
      val (newtree, newpt) = decomposeRewritingType(pt) map {
        case (rewrite, argType, resultType, argName) =>

        // when rewriting a tree that does not introduce a new function
        // argument, i.e., the type T of the tree remains unchanged,
        // also rewrite the expected type pt to T to prevent infinite recursion
        val newpt = if (!rewrite.hasArgument) argType else pt

        // apply rewriting rule
        val newtree = rewrite(argName, tree, resultType)

        // only rewrite tree if the rewriting does not result
        // in compiler errors
        val rewriteTree = silenceReporting {
          context inSilentMode {
            super.typed(newtree.duplicate, mode, newpt)
            !context.reporter.hasErrors
          }
        }

        (if (rewriteTree) newtree else tree) -> newpt

      } getOrElse tree -> pt

      super.typed(newtree, mode, newpt)
    }
  }
}

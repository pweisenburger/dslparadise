package dslparadise
package typechecker

import scala.reflect.internal.Mode

trait CalleeProcessor {
  self: Analyzer with Typers =>

  import global._

  trait CalleeTyper extends Typer {
    self: ParadiseTyper =>

    override def typed(tree: Tree, mode: Mode, pt: Type): Tree = {
      val newtree = decomposeRewritingType(pt) map {
        case (rewrite, _, resultType, argName) =>

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
          val newtree = rewrite(argName, tree, resultType)

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

      super.typed(newtree, mode, pt)
    }
  }
}

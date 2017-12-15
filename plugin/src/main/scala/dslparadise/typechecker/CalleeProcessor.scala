package dslparadise
package typechecker

import scala.reflect.internal.Mode
import scala.reflect.internal.Flags

trait CalleeProcessor {
  self: Analyzer with Typers =>

  import global._

  trait CalleeTyper extends Typer {
    self: ParadiseTyper =>

    object RewrittenTree

    case class ExpectedType(pt: Option[Type])

    override def typed(tree: Tree, mode: Mode, pt: Type): Tree = {
      // Lazy values in Scala 2.11 are desugared into an accessor of the form
      // `def x = { x$lzy = expr; x$lzy }`
      // where the whole block `{ x$lzy = expr; x$lzy }` has the appropriate
      // expected type.
      // We skip processing the block and its last statement (i.e., the return
      // value `x$lzy`) by marking them as rewritten trees. Instead, we attach
      // the expected type to the expression `expr` and process `expr` under the
      // expected type, potentially inserting a compiler-generated argument at
      // this point.
      tree match {
        case DefDef(
              mods, _, Seq(), Seq(), _,
              block @ Block(List(Assign(variableAssign, rhs)), variableReturn))
            if (variableAssign equalsStructure variableReturn) &&
               (mods hasFlag Flags.METHOD | Flags.STABLE | Flags.ACCESSOR | Flags.LAZY) =>
          rhs.updateAttachment(ExpectedType(None))
          block.updateAttachment(RewrittenTree)
          variableReturn.updateAttachment(RewrittenTree)

        case Block(List(Assign(_, rhs)), _)
            if rhs.hasAttachment[ExpectedType] =>
          rhs.updateAttachment(ExpectedType(Some(pt)))

        case _ =>
      }

      // get the expected type that we explicitly assigned to this tree;
      // happens when the tree is the expression initializing a lazy value
      // (in Scala 2.11)
      val realpt = tree.attachments.get[ExpectedType] match {
        case Some(ExpectedType(Some(pt))) =>
          tree.removeAttachment[ExpectedType]
          pt

        case _ =>
          pt
      }

      val (newtree, newpt) = decomposeRewritingType(realpt) collect {
        case (rewrite, argType, resultType, argName)
          if !tree.hasAttachment[RewrittenTree.type] =>

        // when rewriting a tree that does not introduce a new function
        // argument, i.e., the type T of the tree remains unchanged,
        // also rewrite the expected type pt to T to prevent infinite recursion
        val newpt = if (!rewrite.hasArgument) argType else pt

        // apply rewriting rule
        val newtree = rewrite(argName, tree, resultType).updateAttachment(RewrittenTree)

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

package dslparadise
package typechecker

import scala.reflect.internal.Mode
import java.lang.reflect.InvocationTargetException

trait CallProcessor {
  self: Analyzer with Typers =>

  import global._

  trait CallTyper extends Typer {
    self: ParadiseTyper =>

    val adaptToImplicitMethod = try {
      val adaptToImplicitMethod =
        getClass.getSuperclass.getDeclaredMethod(
          "adaptToImplicitMethod$1",
          classOf[MethodType], classOf[Tree], classOf[Int], classOf[Type], classOf[Tree])
      adaptToImplicitMethod.setAccessible(true)
      Some(adaptToImplicitMethod)
    }
    catch { case _: NoSuchMethodException => None }

    if (adaptToImplicitMethod.isEmpty)
      warning(
        "DSL Paradise Plugin: `adaptToImplicitMethod` method not found in Typer. " +
        "Call-site implicit inference for implicit function types will not work.")

    object HasAppliedArgument

    def anonymousArgumentMethodType(argType: Type, resultType: Type) =
      MethodType(List(NoSymbol newSyntheticValueParam argType), resultType)

    override def adapt(tree: Tree, mode: Mode, pt: Type, original: Tree): Tree = {
      val adaptedTree = adaptToImplicitMethod map { adaptToImplicitMethod =>
        tree match {
          case Select(_, _) | Apply(_, _) | Ident(_)
              if tree.tpe != pt && !tree.hasAttachment[HasAppliedArgument.type] =>

            decomposeRewritingType(tree.tpe) collect {
              case (rewrite, argType, resultType, _)
                if rewrite.hasImplicitArgument =>

              // temporarily fake tree type to have a method type,
              // so the existing implicit resolution for method arguments works
              val realTreeType = tree.tpe
              val realTreeSymbolInfo = tree.symbol.info

              tree setType anonymousArgumentMethodType(argType, resultType)
              tree.symbol setInfo tree.tpe

              // only invoke implicit resolution if it does not result
              // in compiler errors
              val adaptTree = context inSilentMode {
                try {
                  adaptToImplicitMethod.invoke(
                    this, tree.tpe, tree, Int box mode.bits, pt, original)
                  !context.reporter.hasErrors
                }
                catch {
                  // Since we faked the tree type, we may run into a situation
                  // where type-checking tries to treat the tree symbol as a
                  // method, which it is not, resulting in an exception.
                  // Assumingly, we should not resolve implicit values in this
                  // case.
                  case _: InvocationTargetException => false
                }
              }

              // invoke existing implicit resolution
              val adaptedTree =
                if (adaptTree)
                  adaptToImplicitMethod.invoke(
                    this, tree.tpe, tree, Int box mode.bits, pt, original)
                  .asInstanceOf[Tree]
                else
                  tree

              // restore real type of the tree
              tree setType realTreeType
              tree.symbol setInfo realTreeSymbolInfo

              // since we get a tree with resolved implicit arguments for methods,
              // but we actually have a tree of a function type, we need to manually
              // insert correctly-typed `apply` calls for the function
              var treeFound = false
              var applyInserted = false

              def insertApplies(subtree: Tree): Tree = subtree match {
                case _ if subtree == tree =>
                  treeFound = true
                  subtree

                case Apply(fun, args @ Seq(_)) =>
                  decomposeRewritingType(fun.tpe) collect {
                    case (rewrite, argType, resultType, _)
                      if rewrite.hasImplicitArgument =>

                    applyInserted = true

                    val applySymbol = fun.tpe decl nme.apply
                    val apply = (Select(insertApplies(fun), nme.apply)
                      setType anonymousArgumentMethodType(argType, resultType)
                      setSymbol applySymbol)

                    Apply(apply, args) setType resultType setSymbol applySymbol

                  } getOrElse
                    (Apply(insertApplies(fun), args)
                      setType subtree.tpe
                      setSymbol subtree.symbol)

                case Select(qual, name) =>
                  (Select(insertApplies(qual), name)
                    setType subtree.tpe
                    setSymbol subtree.symbol)

                case _ =>
                  subtree
              }

              val fixedTree = insertApplies(adaptedTree)

              if (treeFound && applyInserted) fixedTree else tree

            } getOrElse tree

          case _ =>
            tree
        }
      } getOrElse tree

      super.adapt(adaptedTree, mode, pt, original)
    }

    override def typed1(tree: Tree, mode: Mode, pt: Type): Tree = {
      val fun = tree match {
        case Apply(Select(fun, nme.apply) , Seq(_)) => Some(fun)
        case Apply(fun, Seq(_)) => Some(fun)
        case _ => None
      }

      fun map { fun =>
        // make sure no implicit arguments are resolved for a function
        // which is already applied to an argument
        fun.updateAttachment(HasAppliedArgument)
        val typedtree = super.typed1(tree, mode, pt)
        fun.removeAttachment[HasAppliedArgument.type]
        typedtree

      } getOrElse super.typed1(tree, mode, pt)
    }
  }
}

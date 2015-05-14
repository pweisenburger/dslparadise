package dslparadise
package typechecker

import scala.reflect.NameTransformer
import scala.reflect.internal.Mode
import dslparadise._

trait Typers {
  self: Analyzer =>

  import global._

  trait ParadiseTyper extends Typer with TyperContextErrors {
    override def typedArg(arg: Tree, mode: Mode, newmode: Mode, pt: Type): Tree = {
//      val pre = typeOf[dslparadise.`package`.type]

      pt match {
//        case TypeRef(`pre`, sym, _) if sym.name.decodedName.toString == "implicit =>" =>
        case TypeRef(_, sym, _)
          if NameTransformer.decode(sym.fullName) == "dslparadise.implicit =>" =>

          val convertArg = context inSilentMode {
            super.typedArg(arg.duplicate, mode, newmode, pt)
            context.reporter.hasErrors
          }

          val newarg = if (convertArg) {
            val newarg = q"{ implicit! => $arg }"

            val keepArg = context inSilentMode {
              super.typedArg(newarg.duplicate, mode, newmode, pt)
              context.reporter.errors exists { _.errPos == NoPosition }
            }

            if (keepArg) arg else newarg
          }
          else
            arg

          super.typedArg(newarg, mode, newmode, pt)

        case _ =>
          super.typedArg(arg, mode, newmode, pt)
      }
    }
  }
}

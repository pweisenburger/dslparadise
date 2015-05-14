package dslparadise
package typechecker

import scala.reflect.internal.Mode
import dslparadise._

trait Typers {
  self: Analyzer =>

  import global._

  trait ParadiseTyper extends Typer with TyperContextErrors {
    override def typedArg(arg: Tree, mode: Mode, newmode: Mode, pt: Type): Tree = {
      if (pt != WildcardType && pt <:< typeOf[ImplicitFunction1[_, _]]) {
        val convertArg = context inSilentMode {
          super.typedArg(arg.duplicate, mode, newmode, pt)
          context.reporter.hasErrors
        }

        val newarg = if (convertArg) {
          val newarg =
            q"""_root_.dslparadise.ImplicitFunction1 {
              implicit! => $arg
            }"""

          val keepArg = context inSilentMode {
            super.typedArg(newarg.duplicate, mode, newmode, pt)
            context.reporter.errors exists { _.errPos == NoPosition }
          }

          if (keepArg) arg else newarg
        }
        else
          arg

        super.typedArg(newarg, mode, newmode, pt)
      }
      else
        super.typedArg(arg, mode, newmode, pt)
    }
  }
}

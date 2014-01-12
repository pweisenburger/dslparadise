import org.dslparadise.annotations._

object Main extends App {
  def f(a: (Int @Implicit) ⇒ Int) = a(8)

  def g(implicit x: Int) = x + 3

  f(8 + g)
}

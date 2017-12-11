import dslparadise._

object Main extends App {
  ImplicitContextPropagation
  ScopePropagation
  StaticScopeInjection
  ImplicitFunctionTypes
}


object ImplicitContextPropagation {
  def f(a: Int `implicit =>` String) = println(a(5))
  def g(implicit x: Int) = x.toString

  f("Hi, " + g)
  f { implicit imparg => "Hi, " + g }
}


object ScopePropagation {
  class Thingy {
    val u = 6
    val v = 7
  }

  def f(a: Thingy `import =>` Int) = println(a(new Thingy))

  f(4 + u - v)
  f { imparg => import imparg._; 4 + u - v }
}


object StaticScopeInjection {
  object Thingy {
    val u = 6
  }

  def f(a: Int `import` Thingy.type) = println(a)

  f(u + 1)
  f { import Thingy._; u + 1 }
}


object ImplicitFunctionTypes {
  val a1: Int `implicit =>` Int = 2 * implicitly[Int]
  def a2(s: String): Int `implicit =>` String = (2 * implicitly[Int]) + s

  val b1: Int `implicit =>` (Double `implicit =>` Double) = implicitly[Int] * implicitly[Double]
  def b2(s: String): Int `implicit =>` (Double `implicit =>` String) = (implicitly[Int] * implicitly[Double]) + s

  {
    implicit val i: Int = 2
    implicit val d: Double = 2

    println(a1)
    println(a1(2))
    println(a2("!"))
    println(a2("!")(2))

    println(b1)
    println(b1(2))
    println(b1(2)(2))
    println(b2("!"))
    println(b2("!")(2))
    println(b2("!")(2)(2))
  }
}



object NestedContexts {
  class Context[T]
  class Result
  class A
  class B

  def withContext[T](f: Context[T] `implicit =>` Result `argument name` { type ctx }) = ???


  withContext[A] {
    implicitly[Context[_]] // gets `Context[A]`
    withContext[B] {
      implicitly[Context[_]] // gets `Context[A]`
      new Result
    }
    new Result
  }
}



object JavaRegex {
  class Pattern
  object Pattern {
    def compile(s: String, i: Int `import` Pattern): Pattern = new Pattern

    val MULTILINE = 0
    val CASE_INSENSITIVE = 0
  }

  val pattern = Pattern.compile(
    "[a-z]",
    MULTILINE | CASE_INSENSITIVE
  )
}


object SprayHttpResponse {
  class HttpResponse
  object HttpResponse {
    def apply(
      a: StatusCode `import` StatusCodes.type,
      b: HttpEntity `import` HttpEntity.type,
      c: List[HttpHeader] `import` HttpHeaders.type,
      d: HttpProtocol `import` HttpProtocols.type) = new HttpResponse
  }

  class StatusCode
  object StatusCodes {
    val OK = new StatusCode
  }

  class HttpEntity
  object HttpEntity {
    val Empty = new HttpEntity
  }

  class HttpHeader
  object HttpHeaders {
    def Accept(a: Seq[MediaRange] `import` MediaRanges.type) = new HttpHeader
  }

  class MediaRange
  object MediaRanges {
    val `video/*` = new MediaRange
    val `application/*` = new MediaRange
  }

  class HttpProtocol
  object HttpProtocols {
    val `Http/1.1` = new HttpProtocol
  }

  HttpResponse(
    OK,
    Empty,
    List(
      Accept(Seq(
        `video/*`,
        `application/*`
      ))
    ),
    `Http/1.1`
  )
}

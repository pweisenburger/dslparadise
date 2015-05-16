import dslparadise._

object Main extends App {
  ImplicitContextPropagation
  ScopePropagation
  StaticScopeInjection
}


object ImplicitContextPropagation {
  def f(a: Int `implicit =>` String) = println(a(5))
  def g(implicit x: Int) = x.toString

  f("Hi, " + g)
  f { implicit $bang => "Hi, " + g }
}


object ScopePropagation {
  class Thingy {
    val u = 6
    val v = 7
  }
  
  def f(a: Thingy `import._ =>` Int) = println(a(new Thingy))
  
  f(4 + u - v)
  f { $bang => import $bang._; 4 + u - v }
}  


object StaticScopeInjection {
  object Thingy {
    val u = 6
  }
  
  def f(a: Int `import._` Thingy.type) = println(a)
  
  f(u + 1)
  f { import Thingy._; u + 1 }
}



object JavaRegex {
  class Pattern
  object Pattern {
    def compile(s: String, i: Int `import._` Pattern): Pattern = new Pattern

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
      a: StatusCode `import._` StatusCodes.type,
      b: HttpEntity `import._` HttpEntity.type,
      c: List[HttpHeader] `import._` HttpHeaders.type,
      d: HttpProtocol `import._` HttpProtocols.type) = new HttpResponse
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
    def Accept(a: Seq[MediaRange] `import._` MediaRanges.type) = new HttpHeader
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

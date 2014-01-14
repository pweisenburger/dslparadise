DSL Paradise
============

See [corresponding discussion](https://groups.google.com/forum/#!topic/scala-debate/f4CLmYShX6Q) on *scala-debate*.
Credit for the original proposal goes to [@lihaoyi](https://github.com/lihaoyi).

1. Implicit func params: boilerplate-free implicit context propagation
----------------------------------------------------------------------

```scala
def f(a: (implicit Int) ⇒ String) = println(a(5))

def g(implicit x: Int) = x.toString

// f(z: String) is rewritten to f { implicit x$1: Int ⇒ z }
f("Hi, " + g)
> Hi, 5

// compatible with Int ⇒ String (1):
f(implicit x ⇒ "Hi, " + g(x + 1) + g)
> Hi, 65

// compatible with Int ⇒ String (2):
f(x ⇒ "Hi, " + g(x - 1))
> Hi, 4
```

### 2. Import params: Boilerplate-free scope propagation
--------------------------------------------------------

```scala
class Thingy {
  val u = 6
  val v = 7
}

def f(a: (import Thingy) ⇒ Int) = println(a(new Thingy))

// f(z: Int) is rewritten to f { x$1: Thingy ⇒ import x$1._; z }
f(4 + u - v)
> 3

// compatible with Thingy ⇒ Int
f { thingy: Thingy ⇒ 4 + thingy.v }
> 11
```

### 3. Unification of the above with by-name parameter sugar
------------------------------------------------------------

```scala
// deprecate:
// def f(a: ⇒ String) = println(a)

def f(a: () ⇒ String) = println(a())

// f(z: String) rewritten to f(() ⇒ z)
f("foo")
> foo

// compatible with () ⇒ String
f(() ⇒ "foo")
```

### Summary

* `A` can be used in place of `(implicit X) ⇒ A`, receving an **anonymous implicit value** into scope
* `A` can be used in place of `(import X) ⇒ A`, receving **members of X** into scope
* `A` can be used in place of `() ⇒ A`, receiving nothing

While I understand that the third point is controversial,
it’s worth pointing out that there is reasoning behind it,
which goes beyond just establishing similarities.

First, I don’t (yet) see anything wrong with converting `A` to `() ⇒ A`.
(Let me know if you do.)
One has to watch out for side-effects due to possible multiple evaluation,
but that is the case with `a: ⇒ A` as well.

Second, `a: ⇒ A` might be misleading as to when the evaluation
of `a` occurs. Explicit `a: () ⇒ A` is much better, because
one is forced to write `a()`.

Third, by-name values — unlike `Function0`s — are not representable on their own.
Allowing conversion from A to () ⇒ A would render things like [`Thunk`](https://github.com/stanch/macroid/blob/master/src/main/scala/org/macroid/util/Thunk.scala)
obsolete, because you’ll no longer need to write horrible `() ⇒ x` or slightly better `Thunk(x)` everywhere.
(Note, that you’ll still be able to write `() ⇒ x` if you seek explicitness!)

Overall, my impression is that
this change reduces the amount of magic, rather than otherwise.

Use cases
=========

Import and Implicit func params provide a mechanism for reducing unnecessary boilerplate in a variety of contexts, including:

- [Enums and Enum-like arguments](#enums-and-enum-like-arguments)
- [Propagation of implicit context](#propagation-of-implicit-context)
- [Tighter-scoping of contextual identifiers](#tighter-scoping-of-contextual-identifiers)
- [Avoiding boilerplate 'self' parameters](#avoiding-boilerplate-self-parameters)

Enums and Enum-like arguments
-----------------------------

```scala
import java.util.regex.Pattern
val pattern = Pattern.compile(
  "[a-z]",
  Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
)
```

The above snippet is taken from the `java.util.regex`, but the API is similar to what you see in many Scala libraries: you need to pass in some sort of slag to a function, it doesn't really matter what. In the above case, one question you may ask is: why do you need to prefix `MULTILINE` and `CASE_INSENSITIVE` with `Pattern.`? One alternative you have is to simply import everythign into the global namespace, as in:

```scala
import java.util.regex.Pattern
import Pattern._
val pattern = Pattern.compile(
  "[a-z]",
  MULTILINE | CASE_INSENSITIVE
)
```

Which solves the verbosity problem, but at a cost of namespace pollution: now MULTILINE and CASE_INSENSITIVE are sitting taking up room in the global namespace, when you really only need them as an argument to `Pattern.compile`. This tension between clean-namespace-but-verbose and dirty-namespace-but-convenient could be solved by Import Params, e.g. if `Pattern.compile` was defined as:

```scala
def compile(s: String, flags: (import Pattern.type) => Int) = ...
```

You could then write:

```scala
import java.util.regex.Pattern
val pattern = Pattern.compile(
  "[a-z]",
  MULTILINE | CASE_INSENSITIVE
)
```

and `MULTILINE` and `CASE_INSENSITIVE` will then be brought into scope only in the context of the `compile` call, allowing you both clean namespaces and minial verbosity at the callsite.

A similar situation exists for [Spray](http://spray.io/documentation/1.1-SNAPSHOT/api/index.html#spray.http.HttpResponse)'s HTTP api:

```scala
HttpResponse(
  StatusCodes.OK,
  HttpEntity.Empty,
  List(
    HttpHeaders.Accept(Seq(
      MediaRanges.`video/*`,
      MediaRanges.`application/*`,
    )),

  ),
  HttpProtocols.`Http/1.1`
)
```

This shows the problem taken to the extreme: why should I have to say that the `StatusCode` `OK` comes from `StatusCodes`, when every `StatusCode` comes from `StatusCodes`? Again, you could simply import everything, but that would incur a heavy tax of namespace pollution. with Import Params, you could write:

```scala
HttpResponse(
  OK,
  Empty,
  List(
    Accept(Seq(
      `video/*`,
      `application/*`,
    )),

  ),
  `Http/1.1`
)
```

Without any of the names escaping their use-site and polluting the global namespace.

Propagation of implicit context
-------------------------------

The propagation of implicit contexts into HoFs has always been a sticking point for many Scala libraries. Consider SLICK's [api](http://slick.typesafe.com/doc/2.0.0-M3/connection.html), which lets you pass a database session into a block of code either via an implicit parameter:

```scala
db.withSession { implicit session => query.list }
```

or via a `DynamicVariable`:

```scala
db.withDynSession { query.list }
```

This is again a force tradeoff of conciseness and correctness: clearly passing a session using a `DynamicVariable` (which is just a global mutable cell) is more convenient, whereas passing around an implicit everywhere is just annoying boilerplate. However, if you tried to do something clever with the dynamic session:

```scala
db.withDynSession { Future{ query.list } }
```

It may fail, since the global mutable cell may have been modified before the `Future` was run! Using an implicit session:

```scala
db.withSession { implicit session => Future{ query.list } }
```

Works great, but is quite a mouthful to type and to read each time.

This is an unfortunate, because you now have to make an annoying trade-off where there is no good answer:

- Either you go for the annoying-but-safe `implicit` scope
- Or the convenient-but-dangerous dynamic scope

With Implicit Func params, you could define `withSession` as:

```scala
def withSession[T](thunk (implicit Session) => T)
```

Or equivalently using Import params:

```scala
class ImplicitHolder{
  implicit val session: Session = ...
}
def withSession[T](thunk (import ImplicitHolder) => T)
```

In which case you could write:

```scala
db.withSession { Future{ query.list } }
```

and have it desugar to an implicit variable injection, providing both conciseness as well as proper/safe lexical scoping and solving the terrible dilemma we faced before!

Apart from SLICK, other libraries like [Scala-STM](http://nbronson.github.io/scala-stm/) and [Scala.Rx](https://github.com/lihaoyi/scala.rx) and have a similar necessity of passing around an implicit context. They may have made different choices when faced with the dilemma above (Scala.Rx went with DynamicVariable, Scala-STM and SLICK give a choice of Dynamic or Implicit) but all could have benefited from an Import/Implicit func param to provide an API that is both safe and concise.


Tighter-scoping of contextual identifiers
-----------------------------------------

```scala
import scala.async.Async.{async, await}

val future = async {
  val f1 = async { ...; true }
  val f2 = async { ...; 42 }
  if (await(f1)) await(f2) else 0
}
```

Scala-Async provides a macro-based DSL with two main identifiers: `async` and `await`. `await` is defined to be only meaningful within the context of an `async` block. However, as Scala-Async behaves today `await` is simply sitting in the global scope cluttering the namespace, and any attempt to use it results in a runtime error.

A few other possible usage patterns for Scala-Async are shown below:

```scala
import scala.async.Async.async

val future = async {
  import scala.async.Async.await
  val f1 = async { ...; true }
  val f2 = async { ...; 42 }
  if (await(f1)) await(f2) else 0
}
```
```scala
import scala.async.Async.async

val future = async { await =>
  val f1 = async { ...; true }
  val f2 = async { ...; 42 }
  if (await(f1)) await(f2) else 0
}
```
```scala
import scala.async.Async.async

val future = async { ctx =>
  import ctx._
  val f1 = async { ...; true }
  val f2 = async { ...; 42 }
  if (await(f1)) await(f2) else 0
}
```

All of these solve the problen of `await` being too broadly scoped, but at a cost of boilerplate and inconvenience. Given that the inconvenience is felt at every callsite, the Scala-Async developers have chosen to simply import `await` globally, providing convenience at a cost of safety.

With an Import param, you could define `async` as:

```scala
object Holder{
  val await = ???
}
macro def async[T](thunk: (import Holder) => T): Future[T] = ???
```

Which would give you a callsite syntax:

```scala
import scala.async.Async.async

val future = async {
  val f1 = async { ...; true }
  val f2 = async { ...; 42 }
  if (await(f1)) await(f2) else 0
}
```

Where `await` is conveniently injected *only* into the scope in which it was meaningful: inside the `await{}` block. This allows both convenience as well as safety and hygiene: `await` is no longer cluttering up your global namespace or waiting to throw a RuntimeException if you accidentally use it, which is the best of both worlds.

Another example of this kind of use case would be [JScala](https://github.com/nau/jscala), a Scala macro DSL which compiles the captured block to Javascript:

```scala
import org.jscala._
val main = javascript {
  val u = new User("nau", 2)
  val u1Json = eval("(" + inject(json) + ")").as[User] // read User from json string generated above
  val t = new Greeter()
  t.hello(u)
  t.hello(u1Json)
}
```

As you can see, in this case you have several magic identifiers that only have meaning within the context of the `javascript{}` block: `eval`, `inject` and others (e.g. `window`) not shown in this snippet. Currently, these simply exist as names imported into the top-level namespace, cluttering up the namespace and throwing a RuntimeException if accidentally used. With Import params, you could write the same code without the "import everything":

```scala
import org.jscala.javascript
val main = javascript {
  val u = new User("nau", 2)
  val u1Json = eval("(" + inject(json) + ")").as[User] // read User from json string generated above
  val t = new Greeter()
  t.hello(u)
  t.hello(u1Json)
}
```

and have `eval`, `inject`, `window` and other only-makes-sense-within-block variables be tightly (and automatically!) scoped to that block. The last example would be [Scalatags](https://github.com/lihaoyi/scalatags):

```scala
import scalatags.all._
html(
  head(
    script(src:="..."),
    script(
      "alert('Hello World')"
    )
   ),
  body(
    div(
      h1(id:="title", "This is a title"),
      p("This is a big paragraph of text")
    )
  )
)
```

In this case, `src` and `id` represent HTML attributes that only make sense within a Scalatags fragment. However, currently they are imported into the global namespace, and Scalatags itself provides a [variety of different approaches](https://github.com/lihaoyi/scalatags#managing-imports) to allow you to customize exactly how much you wish to trade-off namespace-pollution for use-site convenience, e.g. assigning tags and attributes to the `*` object rather than importing them all directly:

```scala
*.html(
  *.head(
    *.script(*.src:="..."),
    *.script(
      "alert('Hello World')"
    )
   ),
  *.body(
    *.div(
      *.h1(*.id:="title", "This is a title"),
      *.p("This is a big paragraph of text")
    )
  )
)
```

This trades off some call-site convenience in exchange for not having all the HTML attributes sitting in your global namespace where you don't want them.

However, if we made the various tags' `apply` methods take Import params, we could have a use-site syntax like:

```scala
import scalatags.tag
tag.html(
  head(
    script(src:="..."),
    script(
      "alert('Hello World')"
    )
   ),
  body(
    div(
      h1(id:="title", "This is a title"),
      p("This is a big paragraph of text")
    )
  )
)
```

Where apart from the initial `tag`, nothing else is sitting uselessly in the global namespace: all the tags (`head`, `script`, etc.) and the attributes (`src`, `id`) are brought into scope by the import param, allowing you to have all the use-site convenience of the initial dump-everything-in-the-global-namespace solution together with the clean namespaces of the assign-it-to-a-variable approach.

Avoiding boilerplate 'self' Parameters
======================================
```scala
import scalaz.std.option._
import scalaz.std.anyVal._

val b = mutate(a) { $ =>
    $.b.b := 9
    $.b.c.*.b += 10
}
assert(b === B("foo", A(List(4, 8), 9, Some(A(List(), 18, None)))))
```

The above snippet of code is taken from [Mutate](https://github.com/stanch/mutate), a macro lens library to allow easier updates of immutable structures. As you can see, it passes around a `$` parameter, simply to give you a thing to call properties on in order for the lense to know what to do. With Import params, these callsites could be slimmed down to:

```scala
import scalaz.std.option._
import scalaz.std.anyVal._

val b = mutate(a) {
    b.b := 9
    b.c.*.b += 10
}
assert(b === B("foo", A(List(4, 8), 9, Some(A(List(), 18, None)))))
```

Which is a nice improvement.

Another API that could benefit is the experimental library library [Scala.React](https://github.com/ingoem/scala-react), where in the workflow DSL, it passes around a `self` param in order to give a handle which the developer can use to call methods and control the execution of the workflow:

```scala
Events.loop[B] { self =>
  val x = self.await(outer)
  if (p isDefinedAt x) self << p(x)
  self.pause
}
```

With Import parameters, these additional buttons could be simply included into the scope automatically:

```scala
Events.loop[B] {
  val x = await(outer)
  if (p isDefinedAt x) <<(p(x))
  pause
}
```

Making an API that looks a lot more fluent.
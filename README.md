See [corresponding discussion](https://groups.google.com/forum/#!topic/scala-debate/f4CLmYShX6Q) on *scala-debate*.
Credit for the original proposal goes to [@lihaoyi](https://github.com/lihaoyi).

### 1. Boilerplate-free implicit context propagation

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

### 2. Boilerplate-free scope propagation

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

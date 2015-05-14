package object dslparadise {
  import scala.language.implicitConversions

  trait ImplicitFunction1[-T, +R] extends Function1[T, R]

  object ImplicitFunction1 {
    implicit def apply[T, R](f: T => R): T `implicit =>` R =
      new ImplicitFunction1[T, R] {
        def apply(v: T): R = f(v)
      }
  }

  type `implicit =>`[-T, +R] = ImplicitFunction1[T, R]


//  trait ImportFunction1[-T, +R] extends Function1[T, R]
//
//  object ImportFunction1 {
//    implicit def apply[T, R](f: T => R): T `import._ =>` R =
//      new ImportFunction1[T, R] {
//        def apply(v: T): R = f(v)
//      }
//  }
//
//  type `import._ =>`[-T, +R] = ImportFunction1[T, R]
//
//
//  trait ImportValue[+T, +I] {
//    val value: T
//  }
//
//  object ImportValue {
//    implicit def apply[T](v: T): T `import._` Nothing =
//      new ImportValue[T, Nothing] {
//        val value: T = v
//      }
//  }
//
//  type `import._`[+T, +I] = ImportValue[T, I]
}

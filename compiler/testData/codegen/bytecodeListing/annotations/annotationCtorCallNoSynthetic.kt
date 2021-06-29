// WITH_RUNTIME
// IGNORE_BACKEND: JVM
// !LANGUAGE: +InstantiationOfAnnotationClasses

annotation class Foo(val bar: Bar)

annotation class Bar

@Foo(Bar())
fun box() {
}
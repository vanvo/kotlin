fun <T> x(): T = TODO()

fun main() {
    x<Int>()
    println(1)
    val x = X()
    x.foo()
}

class X {
    fun foo() {}
}
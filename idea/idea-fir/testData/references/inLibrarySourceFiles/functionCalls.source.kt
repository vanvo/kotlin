// FILE: functionCalls.kt
fun <T> x(): T = TODO/*kotlin.TODO*/()

fun main() {
    x/*x*/<Int/*kotlin.Int*/>()
    println/*kotlin.io.println*/(1)
    val x = X()
    x.foo/*X.foo*/()
}

class X {
    fun foo() {}
}
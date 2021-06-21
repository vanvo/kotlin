// FILE: main.kt
class A : B, C {
    override fun foo(x: Int) {
    }

    override fun foo<caret>(x: String) {
    }
}

// FILE: B.kt
interface B: C, D

// FILE: C.kt
interface C {
    fun foo(x: Int)
    fun foo(x: String)
}

// FILE: D.kt
interface D {
    fun foo(x: Int)
    fun foo(x: String)
}


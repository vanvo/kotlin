interface A {
    fun get(x: Int)
}

class B : A by object : A {}
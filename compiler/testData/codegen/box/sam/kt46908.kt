fun interface Foo1 {
    fun invoke()
}

fun interface Foo2 : Foo1
fun interface Foo3 : () -> Unit
fun interface Foo4 : Foo3

fun <T> test(foo: T) {
    if (foo.toString() != "foobar") throw AssertionError("expected 'foobar' but found $foo")
}

fun box(): String {
    val foo1 = object : Foo1 {
        override fun invoke() = Unit
        override fun toString() = "foobar"
    }

    val foo2 = object : Foo2 {
        override fun invoke() = Unit
        override fun toString() = "foobar"
    }

    val foo3 = object : Foo3 {
        override fun invoke() = Unit
        override fun toString() = "foobar"
    }

    val foo4 = object : Foo4 {
        override fun invoke() = Unit
        override fun toString() = "foobar"
    }

    test<Foo1>(foo1)
    test<Foo2>(foo2)
    test<Foo3>(foo3)
    test<Foo4>(foo4)

    return "OK"
}
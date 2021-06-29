@CompileTimeCalculation
fun <T> echo(val a: T) = a

@CompileTimeCalculation
fun compileTimeInt() = 42

fun notCompileTimeInt() = 0

inline fun singleCall(val a: Int): Int {
    return a + 1
}

inline fun withSingleVariable(val a: Int): Int {
    val b = a * 2
    return a + b
}

inline fun withCompileTimeCall(): Int {
    val b = compileTimeInt()
    return b
}

inline fun withNotCompileTimeCode(val a: Int): Int {
    val b = a + notCompileTimeInt()
    return b
}

inline fun withNotCompileTimeArgs(): Int {
    val b = echo(notCompileTimeInt())
    val c = b * compileTimeInt()
    return c
}

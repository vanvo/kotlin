// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
annotation class Marker

@Marker
interface Some

abstract class User {
    abstract fun createSome(): Some
    fun Some?.onSome() {}
    fun withSome(some: Some? = null) {}

    fun use() {
        val something = createSome()
        val somethingOther: Some = createSome()
        null.onSome()
        withSome()
    }
}

data class DataClass(@property:Marker val x: Int)

fun useDataClass(d: DataClass) {
    // Should have error in both
    d.x
    val (x) = d
}

typealias My = Some

fun my(my: My) {}

fun your(my: Some) {}
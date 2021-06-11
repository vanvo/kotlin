// FIR_IDENTICAL
// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
annotation class Protected

@OptIn(Protected::class)
abstract class Owner {
    @Protected
    abstract val someProperty: Int
}

@OptIn(Protected::class)
abstract class Derived(value: Int) : Owner() {
    final override var someProperty: Int = value
        private set
}

class User : Derived(42) {
    fun use(): Int {
        return someProperty
    }
}

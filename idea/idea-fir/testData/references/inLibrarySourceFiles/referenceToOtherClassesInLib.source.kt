// FILE: referenceToOtherClassesInLib.kt
class X: Y/*Y*/() {
    override val z: Z/*Z*/ = Z()
}

abstract class Y {
    abstract val z: Z/*Z*/
}

class Z {

}

class A {
    private class AInnerPrivate(val str: String) {

    }
}

class Property {
    var <!EXPOSED_PROPERTY_TYPE!>var1<!>: A.AInnerPrivate? = null
        public get() = field
        public set(value) {}
}

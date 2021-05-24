// !LANGUAGE: +MultiPlatformProjects
// MODULE: m1-common
// FILE: common.kt

header class My

header fun foo(): Int

header val x: String

header object O

header enum class E {
    FIRST
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

<!ACTUAL_MISSING!>impl class My<!>

<!ACTUAL_MISSING!>impl fun foo() = 42<!>

<!ACTUAL_MISSING!>impl val x get() = "Hello"<!>

impl <!ACTUAL_MISSING!>object O<!>

<!ACTUAL_MISSING!>impl enum class E {
    FIRST
}<!>

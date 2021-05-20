// !DIAGNOSTICS: -UNUSED_PARAMETER,-UNUSED_VARIABLE
// SKIP_TXT

// FILE: test.kt
import kotlinx.serialization.*
import kotlinx.serialization.json.*

object Instance

val defaultWarn = <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>
val receiverWarn = <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
val noWarnFormat = Json {encodeDefaults = true}
val receiverNoWarn = noWarnFormat.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
val defaultNoWarn = Json.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)

class SomeContainerClass {
    val memberDefaultWarn = <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>
    val memberReceiverWarn = <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
    val memberNoWarnFormat = Json {encodeDefaults = true}
    val memberReceiverNoWarn = noWarnFormat.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
    val memberDefaultNoWarn = Json.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)

    fun testDefaultWarnings() {
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json() {}<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Any())
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json { /*some comment*/ }<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        val localDefaultFormat = <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(Json.Default) {}<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(Json) {}<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(Json.Default, {})<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(builderAction = {})<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(builderAction = fun JsonBuilder.() {})<!>
        <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json(builderAction = fun JsonBuilder.() = Unit)<!>

        "{}".let {
            <!JSON_FORMAT_REDUNDANT_DEFAULT!>Json {}<!>.<!EXPERIMENTAL_API_USAGE!>decodeFromString<!><Any>(it)
        }
    }

    fun testReceiverWarnings() {
        <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        val encoded = <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>decodeFromString<!><Any>("{}")
        <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.hashCode()
        <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.toString()

        <!JSON_FORMAT_REDUNDANT!>Json(noWarnFormat) {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        <!JSON_FORMAT_REDUNDANT!>Json(builderAction = {encodeDefaults = true})<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        <!JSON_FORMAT_REDUNDANT!>Json(noWarnFormat, {encodeDefaults = true})<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        <!JSON_FORMAT_REDUNDANT!>Json(builderAction = fun JsonBuilder.() {encodeDefaults = true})<!>.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)

        "{}".let {
            <!JSON_FORMAT_REDUNDANT!>Json {encodeDefaults = true}<!>.<!EXPERIMENTAL_API_USAGE!>decodeFromString<!><Any>(it)
        }
    }

    fun testReceiverNoWarnings() {
        val localFormat = Json {encodeDefaults = true}
        localFormat.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        localFormat.<!EXPERIMENTAL_API_USAGE!>decodeFromString<!><Any>("{}")
        localFormat.hashCode()
        localFormat.toString()
    }

    fun testDefaultNoWarnings() {
        val localDefault = Json
        Json.<!EXPERIMENTAL_API_USAGE!>encodeToString<!>(Instance)
        Json.<!EXPERIMENTAL_API_USAGE!>decodeFromString<!><Any>("{}")
        Json.hashCode()
        Json.toString()
        Json(builderAction = this::builder)
        Json(Json.Default, this::builder)
    }

    private fun builder(builder: JsonBuilder) {
        //now its empty builder
    }
}



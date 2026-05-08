package com.aikit.setup.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {

    @Test
    fun primitivesEncode() {
        assertEquals("null", Json.encode(null))
        assertEquals("true", Json.encode(true))
        assertEquals("false", Json.encode(false))
        assertEquals("42", Json.encode(42))
        assertEquals("3.14", Json.encode(3.14))
    }

    @Test
    fun stringsEscapeSpecials() {
        assertEquals("\"hello\"", Json.encode("hello"))
        assertEquals("\"a\\\"b\"", Json.encode("a\"b"))
        assertEquals("\"a\\\\b\"", Json.encode("a\\b"))
        assertEquals("\"line\\nfeed\"", Json.encode("line\nfeed"))
        assertEquals("\"tab\\there\"", Json.encode("tab\there"))
    }

    @Test
    fun controlCharactersUseUnicodeEscape() {
        //  is below 0x20 and not one of the named shortcuts.
        assertEquals("\"\\u0001\"", Json.encode("\u0001"))
    }

    @Test
    fun listsEncodeWithCommas() {
        assertEquals("[1,2,3]", Json.encode(listOf(1, 2, 3)))
        assertEquals("[]", Json.encode(emptyList<Any>()))
    }

    @Test
    fun nestedMapsEncodeInDeclaredOrder() {
        val m = linkedMapOf<String, Any?>(
            "name" to "kit",
            "tags" to listOf("a", "b"),
            "meta" to mapOf("count" to 2),
        )
        assertEquals(
            """{"name":"kit","tags":["a","b"],"meta":{"count":2}}""",
            Json.encode(m),
        )
    }

    @Test
    fun nonStringMapKeyRaises() {
        assertFailsWith<IllegalArgumentException> {
            Json.encode(mapOf(1 to "x"))
        }
    }
}

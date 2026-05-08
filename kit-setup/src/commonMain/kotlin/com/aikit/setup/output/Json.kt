package com.aikit.setup.output

/**
 * Minimal JSON writer for the binary's machine-readable output.
 *
 * The output shapes are simple, fully controlled by us, and always small —
 * so a hand-rolled writer is the right size of dependency. Not a general
 * JSON library: only handles `null`, booleans, integral and floating
 * numbers, strings, lists, and string-keyed maps. Anything else raises.
 */
object Json {

    /** Encodes [value] to a compact JSON string (no whitespace, single line). */
    fun encode(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Double, is Float -> sb.append(value.toString())
            is String -> writeString(sb, value)
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    write(sb, item)
                }
                sb.append(']')
            }
            is Map<*, *> -> {
                sb.append('{')
                var i = 0
                for ((k, v) in value) {
                    if (k !is String) {
                        throw IllegalArgumentException(
                            "JSON object keys must be strings, got ${k?.let { it::class.simpleName }}",
                        )
                    }
                    if (i > 0) sb.append(',')
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, v)
                    i++
                }
                sb.append('}')
            }
            else -> throw IllegalArgumentException(
                "Unsupported JSON value type: ${value::class.simpleName}",
            )
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.code < 0x20) {
                    sb.append("\\u")
                    val hex = c.code.toString(16)
                    repeat(4 - hex.length) { sb.append('0') }
                    sb.append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}

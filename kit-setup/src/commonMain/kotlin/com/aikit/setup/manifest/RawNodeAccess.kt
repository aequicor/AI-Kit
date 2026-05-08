package com.aikit.setup.manifest

/**
 * Convenience accessors over [RawNode] used by the typed manifest layer and
 * the validation rules. The functions never throw on a missing key — every
 * accessor accepts a nullable receiver so callers can chain `field("x")` (which
 * returns null when absent) without sprinkling safe-call operators everywhere.
 *
 * Type coercion (to Int / Boolean) is intentionally minimal; producers of the
 * manifest can always quote-as-string and we treat YAML's typeless scalars as
 * such.
 */

/** Returns the scalar string for a [RawNode.Scalar], else `null`. */
fun RawNode?.stringOrNull(): String? = (this as? RawNode.Scalar)?.value

/** Returns the scalar string, or [default] if the node is null/missing. */
fun RawNode?.stringOr(default: String): String = stringOrNull() ?: default

/** Returns the boolean form of a scalar (`true` / `false`), else `null`. */
fun RawNode?.boolOrNull(): Boolean? = when (stringOrNull()?.lowercase()) {
    "true", "yes", "on" -> true
    "false", "no", "off" -> false
    else -> null
}

/** Returns the integer form of a scalar, else `null`. */
fun RawNode?.intOrNull(): Int? = stringOrNull()?.toIntOrNull()

/** Walks [keys] through nested [RawNode.Mapping]s; returns null if any step misses. */
fun RawNode?.path(vararg keys: String): RawNode? {
    var cur: RawNode = this ?: return null
    for (k in keys) {
        if (cur !is RawNode.Mapping) return null
        cur = cur.entries[k] ?: return null
    }
    return cur
}

/** Returns the child node at [key], or null. Convenience for one-step navigation. */
fun RawNode?.field(key: String): RawNode? =
    (this as? RawNode.Mapping)?.entries?.get(key)

/** Returns [items] for a sequence node, else an empty list. */
fun RawNode?.asList(): List<RawNode> = (this as? RawNode.Sequence)?.items ?: emptyList()

/** Returns sequence items rendered as strings (skipping non-scalars silently). */
fun RawNode?.asStringList(): List<String> = asList().mapNotNull { it.stringOrNull() }

/** Returns mapping entries, or empty map. */
fun RawNode?.asMap(): Map<String, RawNode> =
    (this as? RawNode.Mapping)?.entries ?: emptyMap()

/** True if the node is missing or explicitly null. */
fun RawNode?.isNullish(): Boolean = this == null || this is RawNode.Null

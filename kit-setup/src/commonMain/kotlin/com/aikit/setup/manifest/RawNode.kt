package com.aikit.setup.manifest

/**
 * Untyped representation of a YAML node, mirroring the YAML 1.2 data model
 * (mapping, sequence, scalar, or null) without committing to a schema.
 *
 * Validation rules walk this tree by JSON-pointer-like paths so they can
 * report errors against the original document layout, even before a typed
 * representation of the manifest exists.
 */
sealed class RawNode {

    /** Mapping node — preserves insertion order from the YAML source. */
    data class Mapping(val entries: Map<String, RawNode>) : RawNode()

    /** Sequence node — ordered list of child nodes. */
    data class Sequence(val items: List<RawNode>) : RawNode()

    /**
     * Scalar node — string-typed by default. Type coercion (to int, bool,
     * etc.) is the validator's job, not the loader's, so the original textual
     * form is preserved here.
     */
    data class Scalar(val value: String) : RawNode()

    /** Explicit null (`null`, `~`, or empty value in YAML). */
    data object Null : RawNode()
}

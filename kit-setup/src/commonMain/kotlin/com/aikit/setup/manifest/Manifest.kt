package com.aikit.setup.manifest

/**
 * Parsed kit manifest.
 *
 * The schema is intentionally TBD — this type is the slot the loader produces
 * and the validator and generator consume. Concrete fields will be filled in
 * once the manifest schema is finalized; until then, [raw] carries the
 * loader's untyped view of the YAML so validation rules can already operate
 * against it by JSON-pointer-style paths.
 */
data class Manifest(
    /**
     * Untyped tree representing the parsed YAML document, rooted at the
     * top-level mapping. The tree is preserved verbatim (modulo the YAML
     * library's normalization) so error reporting can quote original paths.
     */
    val raw: RawNode,
)

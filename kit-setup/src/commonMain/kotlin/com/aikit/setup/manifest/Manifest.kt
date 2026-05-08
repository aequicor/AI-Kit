package com.aikit.setup.manifest

/**
 * Parsed kit manifest.
 *
 * Carries both the original [raw] tree (so validation rules can report
 * path-keyed errors against the source layout) and a best-effort [typed]
 * decoding of the same content (so the generator can navigate fields
 * without reaching back into the raw tree). The decoder is non-throwing —
 * fields that are missing or wrong type collapse to documented defaults
 * and the validator catches them before the typed view is consumed.
 */
data class Manifest(
    val raw: RawNode,
    val typed: TypedManifest,
)

package com.aikit.setup.validation

/**
 * One validation problem reported against a manifest.
 *
 * The fields are deliberately tuned for two audiences: the user (who reads
 * [message]/[hint]) and the orchestrating agent (who keys auto-fix logic on
 * [path] and [code]). Codes are stable identifiers; messages may evolve.
 */
data class ValidationError(

    /**
     * JSON-pointer-like path into the manifest, e.g. `"/agents/list/0/name"`.
     * Empty string means the error applies to the document as a whole.
     */
    val path: String,

    /**
     * Stable, machine-readable identifier in `snake_case`. Treat this as part
     * of the public contract: agents pattern-match on it, so changing a code
     * is a breaking change.
     */
    val code: String,

    /** Human-readable explanation of what went wrong. */
    val message: String,

    /** Optional suggestion for how to fix the manifest. May be `null`. */
    val hint: String? = null,
)

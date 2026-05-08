package com.aikit.setup.generation

/**
 * Outcome of running the generator over a manifest.
 *
 * Generation may partially succeed: some files written, others failing due
 * to I/O issues. Both lists are populated independently so callers can
 * report what landed and what didn't.
 */
data class GenerationResult(

    /** Paths (relative to the target root) of files actually written. */
    val generatedFiles: List<String>,

    /** Failures encountered during the run. Empty list = clean success. */
    val errors: List<GenerationError>,
) {
    /** `true` iff no [errors] were recorded. */
    val success: Boolean get() = errors.isEmpty()
}

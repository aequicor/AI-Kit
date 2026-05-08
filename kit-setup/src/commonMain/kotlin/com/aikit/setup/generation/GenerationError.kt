package com.aikit.setup.generation

/**
 * Failure that occurred while writing the kit.
 *
 * Distinct from `ValidationError`: validation runs to completion before
 * generation starts, so any [GenerationError] represents an I/O or runtime
 * problem during file emission — never a schema problem with the manifest.
 */
data class GenerationError(

    /**
     * Path of the file the generator was attempting to write when the failure
     * occurred. May be empty for failures not tied to a specific file (e.g. a
     * top-level emitter crash).
     */
    val path: String,

    /** Stable identifier in `snake_case`, e.g. `write_failed`, `generator_failed`. */
    val code: String,

    /** Human-readable explanation. */
    val message: String,
)

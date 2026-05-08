package com.aikit.setup.manifest

/**
 * Outcome of loading a manifest from disk.
 *
 * Distinguishes the success and failure cases so callers (the verify and
 * generate services) can branch without inspecting exceptions. Failure
 * carries a stable error code suitable for machine consumption.
 */
sealed class LoadResult {

    /** Manifest was found, read, and parsed without error. */
    data class Success(val manifest: Manifest) : LoadResult()

    /** Loading failed at some stage; details are encoded in [code] and [message]. */
    data class Failure(val code: LoadErrorCode, val message: String) : LoadResult()
}

/**
 * Stable identifiers for load failures. Agents key auto-recovery logic off
 * these names — keep them stable across releases. Lowercase form (snake_case)
 * is what gets written into JSON output.
 */
enum class LoadErrorCode {
    /** No file at the requested path. */
    MANIFEST_NOT_FOUND,

    /** File exists but could not be read (permission, I/O failure). */
    READ_FAILED,

    /** File read succeeded but YAML parsing rejected the contents. */
    PARSE_FAILED,
}

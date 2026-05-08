package com.aikit.setup.io

/**
 * Write-side view of the host filesystem.
 *
 * The kit generator depends on this rather than the broader [FileSystem]
 * interface so the type signature alone communicates that generation is the
 * write surface — readers above it (loader, validator) cannot accidentally
 * gain write access through the same parameter.
 */
interface FileWriter {

    /**
     * Creates [path] as a directory and any missing parents along the way.
     * Idempotent — succeeding on a path that already exists is not an error.
     */
    fun mkdirs(path: String)

    /**
     * Writes [content] to [path], replacing any existing file at that path.
     * The contract is unconditional overwrite — callers must perform their
     * own existence checks if they care about preserving prior state.
     */
    fun writeFile(path: String, content: String)
}

package com.aikit.setup.generation

import com.aikit.setup.manifest.Manifest

/**
 * Produces a kit on disk from a validated manifest.
 *
 * The interface is what the `generate` service depends on; concrete emitters
 * (per-provider Claude Code / OpenCode / Cursor / etc.) plug in behind it.
 * Generation always overwrites existing files — that's the contract every
 * implementation must honor.
 */
interface KitGenerator {

    /**
     * Writes the kit described by [manifest] under [targetRoot] (an absolute
     * or working-directory-relative path that already exists or will be
     * created on demand).
     *
     * Returns a [GenerationResult] describing what was written and what
     * failed. Implementations may throw on truly unexpected runtime errors
     * (e.g. NotImplementedError for stubs), and the calling command is
     * responsible for translating those into exit codes.
     */
    fun generate(manifest: Manifest, targetRoot: String): GenerationResult
}

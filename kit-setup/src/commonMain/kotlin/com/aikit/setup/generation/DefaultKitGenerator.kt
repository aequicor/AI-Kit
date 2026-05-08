package com.aikit.setup.generation

import com.aikit.setup.io.FileWriter
import com.aikit.setup.manifest.Manifest

/**
 * Default [KitGenerator] implementation. Currently a slot — concrete emitters
 * for Claude Code, OpenCode, Cursor, and any other supported provider will
 * plug in here once the manifest schema is settled and the per-provider
 * output layouts are decided.
 *
 * Holds a [FileWriter] (rather than the full `FileSystem`) to make it
 * structurally impossible for the generator to read existing files — its
 * job is purely to emit.
 */
class DefaultKitGenerator(
    private val files: FileWriter,
) : KitGenerator {

    override fun generate(manifest: Manifest, targetRoot: String): GenerationResult {
        // Slot. Future implementation: dispatch to per-provider emitters
        // based on the manifest contents, write files via [files], aggregate
        // results into a GenerationResult.
        throw NotImplementedError(
            "Kit generation is not implemented yet. Wire the manifest-driven " +
                "emitters into DefaultKitGenerator before invoking generate.",
        )
    }
}

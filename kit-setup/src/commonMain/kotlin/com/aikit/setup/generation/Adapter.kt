package com.aikit.setup.generation

/**
 * Loaded `target_adapters/<id>/adapter.yaml` package — the runner-specific
 * recipe for *where* and *in what frontmatter* artifacts land.
 *
 * The adapter is the orthogonal counterpart to [Dialect]: dialect decides
 * *what* prose looks like, adapter decides *where* it goes and *what wrapper
 * file* it goes into. Producing both per render target is the contract this
 * generator implements.
 */
data class Adapter(
    val id: String,
    /** Path inside the embedded template tree — used to resolve relative refs. */
    val packagePath: String,
    /** Project-relative directory where the runner reads its config. */
    val configDir: String?,
    /** Project-relative file (or directory, for cursor) for the runner's main instructions. */
    val instructionFile: String?,
    /** Project-relative path to the runner's settings file. */
    val settingsFile: String?,
    /**
     * Per-artifact output paths with `{id}` placeholders. `null` value =
     * adapter does not support that artifact type.
     */
    val artifactPaths: Map<String, String?>,
    /** Per-artifact frontmatter template paths (relative to packagePath). null = none. */
    val artifactFrontmatter: Map<String, String?>,
    /** Capability flags consulted before generating optional artifacts. */
    val capabilities: Map<String, Boolean>,
    /** Path to the settings file template (relative to packagePath). */
    val settingsTemplate: String?,
)

/** Loaded `dialects/<id>/dialect.yaml` — model-family prose package. */
data class Dialect(
    val id: String,
    val packagePath: String,
    val appliesToFamilies: List<String>,
    val wrappers: Map<String, String>,
    val snippetsDir: String?,
)

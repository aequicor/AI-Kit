package com.aikit.setup.generation

import com.aikit.setup.manifest.YamlParser
import com.aikit.setup.manifest.asMap
import com.aikit.setup.manifest.asStringList
import com.aikit.setup.manifest.boolOrNull
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.templates.TemplateRegistry

/**
 * Reads an adapter or dialect package's `adapter.yaml` / `dialect.yaml` from
 * the embedded template tree. Pure transformation: no caching, no I/O. Each
 * `load*` call parses fresh — the template tree is small and immutable.
 */
class PackageLoader(
    private val templates: TemplateRegistry,
    private val parser: YamlParser,
) {

    /** Returns the [Adapter] found at [packagePath] (e.g. `target_adapters/claude-code`). */
    fun loadAdapter(packagePath: String): Adapter {
        val normalized = normalize(packagePath)
        val yamlPath = "$normalized/adapter.yaml"
        val raw = parser.parse(
            templates.read(yamlPath)
                ?: throw GeneratorException("adapter package not found: $yamlPath"),
        )
        val artifactPaths = raw.field("artifact_paths").asMap()
            .mapValues { (_, v) -> v.stringOrNull() }
        val artifactFrontmatter = raw.field("artifact_frontmatter").asMap()
            .mapValues { (_, v) -> v.stringOrNull() }
        val capabilities = raw.field("capabilities").asMap()
            .mapValues { (_, v) -> v.boolOrNull() ?: false }
        return Adapter(
            id = raw.field("id").stringOrNull().orEmpty(),
            packagePath = normalized,
            configDir = raw.field("config_dir").stringOrNull(),
            instructionFile = raw.field("instruction_file").stringOrNull(),
            settingsFile = raw.field("settings_file").stringOrNull(),
            artifactPaths = artifactPaths,
            artifactFrontmatter = artifactFrontmatter,
            capabilities = capabilities,
            settingsTemplate = raw.field("settings_template").stringOrNull(),
            mcpFile = raw.field("mcp_file").stringOrNull(),
            mcpTemplate = raw.field("mcp_template").stringOrNull(),
        )
    }

    /** Returns the [Dialect] found at [packagePath] (e.g. `dialects/anthropic`). */
    fun loadDialect(packagePath: String): Dialect {
        val normalized = normalize(packagePath)
        val yamlPath = "$normalized/dialect.yaml"
        val raw = parser.parse(
            templates.read(yamlPath)
                ?: throw GeneratorException("dialect package not found: $yamlPath"),
        )
        val wrappers = raw.field("wrappers").asMap()
            .mapValues { (_, v) -> v.stringOrNull().orEmpty() }
            .filterValues { it.isNotEmpty() }
        return Dialect(
            id = raw.field("id").stringOrNull().orEmpty(),
            packagePath = normalized,
            appliesToFamilies = raw.field("applies_to_families").asStringList(),
            wrappers = wrappers,
            snippetsDir = raw.field("snippets_dir").stringOrNull(),
        )
    }

    /**
     * Normalizes a manifest-relative package path into a TemplateRegistry key.
     * The manifest writes paths like `./target_adapters/claude-code` — we want
     * `target_adapters/claude-code` (no leading `./`, no trailing slash).
     */
    private fun normalize(p: String): String {
        var s = p.trim()
        while (s.startsWith("./")) s = s.removePrefix("./")
        return s.trimEnd('/')
    }
}

/** Thrown when adapter/dialect packages or template files referenced by the manifest are missing. */
class GeneratorException(message: String) : RuntimeException(message)

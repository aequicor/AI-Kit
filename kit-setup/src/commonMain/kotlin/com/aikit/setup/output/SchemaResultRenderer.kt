package com.aikit.setup.output

import com.aikit.setup.command.ProfileAxisInfo
import com.aikit.setup.command.ProfileEntry
import com.aikit.setup.command.SchemaCatalog

/**
 * Renders a [SchemaCatalog] to a string suitable for emission on stdout.
 *
 * Two implementations ship: [JsonSchemaResultRenderer] (the agent-facing wire
 * format) and [HumanSchemaResultRenderer] (a plain-text tree for humans
 * inspecting the bundle). Picked at the command layer based on `--format`.
 */
interface SchemaResultRenderer {

    /** Returns the rendered representation of [catalog]. */
    fun render(catalog: SchemaCatalog): String
}

/**
 * Renders the catalog as a single-line JSON object consumed by orchestrating
 * agents. Field order is fixed and stable — agents pattern-match on the keys.
 *
 * Wire format:
 * ```
 * { "kit_version": "...",
 *   "manifest_schema_version": "...",
 *   "agents": [...],
 *   "agent_dialect_variants": { "<base>": ["<dialect>", ...], ... },
 *   "commands": [...],
 *   "skills": [...],
 *   "prompt_dialects": [...],
 *   "target_adapters": [...],
 *   "knowledge_sections": [...],
 *   "shared_snippets": [...],
 *   "rules": [...],
 *   "user_prompts": [...],
 *   "profile_axes": [...],
 *   "profiles": [...],
 *   "enums": { "provider_auth": [...], "model_tier": [...], ... } }
 * ```
 */
class JsonSchemaResultRenderer : SchemaResultRenderer {

    override fun render(catalog: SchemaCatalog): String = Json.encode(
        linkedMapOf<String, Any?>(
            "kit_version" to catalog.kitVersion,
            "manifest_schema_version" to catalog.manifestSchemaVersion,
            "agents" to catalog.agents,
            "agent_dialect_variants" to catalog.agentDialectVariants,
            "commands" to catalog.commands,
            "skills" to catalog.skills,
            "prompt_dialects" to catalog.promptDialects,
            "target_adapters" to catalog.targetAdapters,
            "knowledge_sections" to catalog.knowledgeSections,
            "shared_snippets" to catalog.sharedSnippets,
            "rules" to catalog.rules,
            "user_prompts" to catalog.userPrompts,
            "profile_axes" to catalog.profileAxes.map(::axisToMap),
            "profiles" to catalog.profiles.map(::profileToMap),
            "enums" to catalog.enums,
        ),
    )

    private fun axisToMap(axis: ProfileAxisInfo): Map<String, Any?> = linkedMapOf(
        "name" to axis.name,
        "cardinality" to axis.cardinality,
    )

    private fun profileToMap(profile: ProfileEntry): Map<String, Any?> = linkedMapOf(
        "name" to profile.name,
        "axis" to profile.axis,
        "description" to profile.description,
    )
}

/**
 * Renders the catalog as a plain-text tree for humans. Not parsed by agents
 * — they read the JSON form. Layout is intentionally informal: when the
 * bundle changes the easiest sanity check is to skim this output.
 */
class HumanSchemaResultRenderer : SchemaResultRenderer {

    override fun render(catalog: SchemaCatalog): String = buildString {
        appendLine(
            "kit-setup schema  (kit_version ${catalog.kitVersion}, " +
                "manifest ${catalog.manifestSchemaVersion})",
        )
        appendLine()
        appendLine(line("agents", catalog.agents, catalog.agentDialectVariants))
        appendLine(line("prompt_dialects", catalog.promptDialects))
        appendLine(line("target_adapters", catalog.targetAdapters))
        appendLine(line("commands", catalog.commands))
        appendLine(line("skills", catalog.skills))
        appendLine(line("knowledge_sections", catalog.knowledgeSections))
        appendLine(line("shared_snippets", catalog.sharedSnippets))
        appendLine(line("rules", catalog.rules))
        appendLine(line("user_prompts", catalog.userPrompts))
        appendLine(profilesBlock(catalog))
        appendLine()
        append(enumsBlock(catalog))
    }

    /**
     * Renders the enum block — one line per field with its allowed values
     * comma-separated. Mirrors the inline form of [line] for collection labels
     * but the values come from a fixed map rather than a bundled list.
     */
    private fun enumsBlock(catalog: SchemaCatalog): String = buildString {
        val title = "${"enums".padEnd(LABEL_WIDTH)}(${catalog.enums.size})"
        appendLine("$title :")
        for ((field, values) in catalog.enums) {
            appendLine("  $field : ${values.joinToString(", ")}")
        }
        if (isNotEmpty() && last() == '\n') deleteAt(length - 1)
    }

    /**
     * Renders the profiles section grouped by axis with cardinality and a
     * one-line description per entry. Empty axes (no bundled profiles) still
     * surface so the user knows the axis exists but has no presets — useful
     * when a custom profile dropped into `templates/profiles/` is the only
     * one for its axis.
     */
    private fun profilesBlock(catalog: SchemaCatalog): String = buildString {
        val cardinalityByAxis = catalog.profileAxes.associate { it.name to it.cardinality }
        val byAxis = catalog.profiles.groupBy { it.axis }
        val title = "${"profiles".padEnd(LABEL_WIDTH)}(${catalog.profiles.size})"
        appendLine("$title :")
        for (axis in catalog.profileAxes) {
            val entries = byAxis[axis.name].orEmpty()
            val cardinalityLabel = humanCardinality(cardinalityByAxis[axis.name])
            appendLine("  ${axis.name} ($cardinalityLabel, ${entries.size}):")
            if (entries.isEmpty()) {
                appendLine("    (none bundled)")
                continue
            }
            for (entry in entries) {
                val description = entry.description?.let { " — $it" } ?: ""
                appendLine("    * ${entry.name}$description")
            }
        }
        // Drop the trailing newline so the renderer's caller can decide.
        if (isNotEmpty() && last() == '\n') deleteAt(length - 1)
    }

    private fun humanCardinality(raw: String?): String = when (raw) {
        "exactly_one" -> "exactly 1"
        "zero_or_more" -> "0..N"
        null -> "?"
        else -> raw
    }

    private fun line(label: String, items: List<String>): String {
        val title = "${label.padEnd(LABEL_WIDTH)}(${items.size})"
        return "$title : ${items.joinToString(", ")}"
    }

    private fun line(
        label: String,
        items: List<String>,
        variants: Map<String, List<String>>,
    ): String {
        val starred = items.map { name ->
            if (variants.containsKey(name)) "$name*" else name
        }
        val title = "${label.padEnd(LABEL_WIDTH)}(${items.size})"
        val base = "$title : ${starred.joinToString(", ")}"
        if (variants.isEmpty()) return base
        val tail = variants.entries.joinToString("\n") { (k, v) ->
            "  * $k has dialect variants: ${v.joinToString(", ")}"
        }
        return "$base\n$tail"
    }

    companion object {
        private const val LABEL_WIDTH = 20
    }
}

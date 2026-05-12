package com.aikit.setup.command

import com.aikit.setup.cli.KIT_SETUP_VERSION
import com.aikit.setup.manifest.BlockYamlParser
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.YamlParser
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.profile.ProfileAxis
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.templates.TemplateRegistry

/**
 * Manifest schema version this binary expects. Bumped together with the
 * JSON Schema in `templates/schema/kit-manifect.schema.json`. File-level
 * (rather than companion) so it can be referenced from default constructor
 * arguments without forward-reference issues.
 */
const val MANIFEST_SCHEMA_VERSION: String = "1.0.0"

/**
 * Pure use case for the `schema` subcommand: walk the bundled
 * [TemplateRegistry] and group the keys by category so the orchestrating
 * agent can discover what variants are available without reading the binary.
 *
 * No I/O — the registry is already the embedded snapshot. No validation —
 * everything in the bundle is, by construction, a valid choice.
 */
class SchemaService(
    private val templates: TemplateRegistry,
    private val yamlParser: YamlParser = BlockYamlParser(),
    private val kitVersion: String = KIT_SETUP_VERSION,
    private val manifestSchemaVersion: String = MANIFEST_SCHEMA_VERSION,
) {

    /** Builds the catalog of bundled variants. */
    fun catalog(): SchemaCatalog {
        val variants = collectAgentVariants()
        return SchemaCatalog(
            kitVersion = kitVersion,
            manifestSchemaVersion = manifestSchemaVersion,
            agents = variants.bases,
            agentDialectVariants = variants.byBase,
            commands = listSimpleNames(prefix = "commands/"),
            skills = listTopLevelDirs(prefix = "skills/"),
            promptDialects = listTopLevelDirs(prefix = "dialects/"),
            targetAdapters = listTopLevelDirs(prefix = "target_adapters/"),
            knowledgeSections = listSimpleNames(prefix = "knowledge/"),
            sharedSnippets = listSimpleNames(prefix = "_shared/snippets/"),
            rules = listRulePaths(),
            userPrompts = listSimpleNames(prefix = "user-prompts/"),
            profileAxes = profileAxesInfo(),
            profiles = collectProfiles(),
            enums = enumValues(),
        )
    }

    /**
     * Allowed values for enum-typed manifest fields the validator / resolver
     * enforces. Hand-written rather than reflected because Kotlin/Native does
     * not expose enum companions cleanly and these strings are a public
     * contract — they must match the `when` branches in
     * [com.aikit.setup.model.ManifestModel] and
     * [com.aikit.setup.validation.rules.ProviderAuthRule].
     *
     * Adding a new value is a two-touch change: extend the parser branch and
     * extend this list. Renaming or removing a value is a breaking change —
     * agents pattern-match on these strings.
     */
    private fun enumValues(): Map<String, List<String>> = linkedMapOf(
        "provider_auth" to listOf("api_key", "subscription", "none"),
        "model_tier" to listOf("fast", "balanced", "reasoner"),
        "cost_hint" to listOf("cheap", "balanced", "premium"),
        "knowledge_store_kind" to listOf("filesystem", "mcp", "http", "composite"),
    )

    /**
     * Static metadata about each profile axis. Cardinality is not derived
     * from the bundle — it is the contract enforced by ProfileResolver, so
     * we hand-write the mapping here. Adding a new axis means adding it to
     * [ProfileAxis] AND extending this list (and the resolver's checks).
     */
    private fun profileAxesInfo(): List<ProfileAxisInfo> = ProfileAxis.entries.map { axis ->
        ProfileAxisInfo(
            name = axis.dirName,
            cardinality = when (axis) {
                ProfileAxis.LANGUAGE -> "exactly_one"
                ProfileAxis.FRAMEWORK -> "zero_or_more"
                ProfileAxis.CAPABILITY -> "zero_or_more"
            },
        )
    }

    /**
     * Walks `profiles/<axis>/<name>.yaml`, parses each, and pulls out the
     * front-matter the agent needs to choose between presets.
     *
     * Files that fail to parse or omit `_profile_axis` are skipped silently —
     * the schema command must always succeed since the bundle is fixed at
     * build time. A broken profile is a build-time bug, not a runtime concern.
     *
     * Sort order: by axis (in declaration order of [ProfileAxis]) and then by
     * name. Stable across rebuilds so the JSON output is diffable.
     */
    private fun collectProfiles(): List<ProfileEntry> {
        val byAxis = ProfileAxis.entries.associateWith { mutableListOf<ProfileEntry>() }

        for (path in templates.list("profiles/")) {
            val tail = path.removePrefix("profiles/")
            val parts = tail.split('/')
            // Expect exactly `<axis>/<name>.yaml` — anything else (README,
            // schema, nested dirs) is not a profile.
            if (parts.size != 2) continue
            val (axisDir, fileName) = parts
            if (!fileName.endsWith(".yaml")) continue
            val axis = ProfileAxis.fromDirName(axisDir) ?: continue
            val name = fileName.removeSuffix(".yaml")

            val body = templates.read(path) ?: continue
            val raw = try {
                yamlParser.parse(body)
            } catch (_: Throwable) {
                continue
            }
            if (raw !is RawNode.Mapping) continue

            // Cross-check declared axis with directory; skip on mismatch so
            // the catalog never advertises a broken file as a usable profile.
            val declaredAxis = raw.field("_profile_axis").stringOrNull()
            if (declaredAxis != axis.dirName) continue

            byAxis.getValue(axis).add(
                ProfileEntry(
                    name = name,
                    axis = axis.dirName,
                    description = raw.field("_profile_description").stringOrNull(),
                ),
            )
        }

        return byAxis.values.flatMap { entries -> entries.sortedBy { it.name } }
    }

    /**
     * Splits files under `prompts/` into base agent ids and per-dialect overrides.
     *
     * Filename grammar:
     *  - `Name.md`            → base prompt for agent `Name`
     *  - `Name.<dialect>.md`  → per-family override for that agent
     *
     * Anything else under `prompts/` is ignored; that keeps the catalog
     * honest if the bundle ever picks up an unexpected file.
     */
    private fun collectAgentVariants(): AgentPrompts {
        val bases = mutableSetOf<String>()
        val byBase = mutableMapOf<String, MutableSet<String>>()
        for (path in templates.list("prompts/")) {
            val name = path.removePrefix("prompts/")
            if (!name.endsWith(".md") || name.contains('/')) continue
            val stem = name.removeSuffix(".md")
            val parts = stem.split('.')
            when (parts.size) {
                1 -> bases.add(parts[0])
                2 -> byBase.getOrPut(parts[0]) { mutableSetOf() }.add(parts[1])
                else -> Unit
            }
        }
        return AgentPrompts(
            bases = bases.sorted(),
            byBase = byBase.entries
                .sortedBy { it.key }
                .associate { (k, v) -> k to v.sorted() },
        )
    }

    /**
     * Returns sorted rule paths under `rules/`. Two shapes are accepted:
     *  - `<id>`              for rules at the root (language-agnostic).
     *  - `<scope>/<id>`      for scoped rules, where `<scope>` is either a language
     *                        name from `manifest.stack.languages` or the literal
     *                        `shared` (always-applied).
     *
     * Deeper paths are intentionally ignored — the rule scope is a single segment.
     * Matches the path shape consumed by [DefaultKitGenerator.listRules].
     */
    private fun listRulePaths(): List<String> {
        val result = mutableSetOf<String>()
        for (path in templates.list("rules/")) {
            if (!path.endsWith(".md")) continue
            val tail = path.removePrefix("rules/").removeSuffix(".md")
            if (tail.count { it == '/' } <= 1) result.add(tail)
        }
        return result.sorted()
    }

    /**
     * Returns sorted basenames (without `.md`) of files directly under
     * [prefix]. Skips entries that recurse into a subdirectory.
     */
    private fun listSimpleNames(prefix: String): List<String> {
        val result = mutableSetOf<String>()
        for (path in templates.list(prefix)) {
            val tail = path.removePrefix(prefix)
            if (tail.contains('/') || !tail.endsWith(".md")) continue
            result.add(tail.removeSuffix(".md"))
        }
        return result.sorted()
    }

    /**
     * Returns sorted names of immediate subdirectories under [prefix],
     * derived from the first path segment of each entry that lives at least
     * one level deep.
     */
    private fun listTopLevelDirs(prefix: String): List<String> {
        val result = mutableSetOf<String>()
        for (path in templates.list(prefix)) {
            val tail = path.removePrefix(prefix)
            val slash = tail.indexOf('/')
            if (slash <= 0) continue
            result.add(tail.substring(0, slash))
        }
        return result.sorted()
    }

    private data class AgentPrompts(
        val bases: List<String>,
        val byBase: Map<String, List<String>>,
    )
}

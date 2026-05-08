package com.aikit.setup.command

/**
 * Snapshot of the variants embedded in this binary's template tree.
 *
 * Returned by [SchemaService] and rendered by `SchemaResultRenderer`. Every
 * field is a flat list of stable identifiers an orchestrating agent can drop
 * straight into a manifest (`agents[].id`, `prompt_dialects[].id`,
 * `target_adapters[].id`, etc.). The catalog is derived from the
 * [com.aikit.setup.templates.TemplateRegistry] that ships in the binary —
 * editing `templates/` after install does not change it.
 *
 * All list-shaped fields are returned sorted ascending so the output is
 * stable across rebuilds and trivially diff-able.
 */
data class SchemaCatalog(
    val kitVersion: String,
    val manifestSchemaVersion: String,
    val agents: List<String>,
    val agentDialectVariants: Map<String, List<String>>,
    val commands: List<String>,
    val skills: List<String>,
    val promptDialects: List<String>,
    val targetAdapters: List<String>,
    val knowledgeSections: List<String>,
    val sharedSnippets: List<String>,
    val rules: List<String>,
    val userPrompts: List<String>,
    /**
     * Profile axes with their cardinality contract — how many profiles of
     * each axis a manifest's `stack.profiles[]` may carry. Mirrors the
     * runtime check inside [com.aikit.setup.manifest.profile.ProfileResolver].
     */
    val profileAxes: List<ProfileAxisInfo>,
    /**
     * Every bundled profile, sorted by axis then name. The agent picks names
     * from here when authoring `manifest.stack.profiles[]` and uses [axis] +
     * [profileAxes] cardinality to know how many of each may appear.
     */
    val profiles: List<ProfileEntry>,
    /**
     * Allowed values for enum-typed manifest fields the validator enforces
     * (e.g. `providers[].auth`, `models[].tier`). Keys are agent-facing
     * field names (`provider_auth`, `model_tier`, `cost_hint`,
     * `knowledge_store_kind`); values are the canonical lowercase strings.
     *
     * Aliases accepted by the parser (`api-key`, `runner_managed`, …) are
     * deliberately not advertised — agents should write the canonical form.
     * Iteration order matches insertion order; the renderer relies on it for
     * stable output.
     */
    val enums: Map<String, List<String>>,
)

/**
 * Public-facing axis description rendered into the schema catalog.
 *
 * [cardinality] is a stable enum string (`exactly_one` | `zero_or_more`) —
 * agents pattern-match on it to enforce limits client-side before invoking
 * verify.
 */
data class ProfileAxisInfo(
    val name: String,
    val cardinality: String,
)

/**
 * One bundled profile, derived from `templates/profiles/<axis>/<name>.yaml`.
 *
 * [name] is the YAML basename (and the value the agent drops into
 * `stack.profiles[]`); [axis] equals the parent directory and the YAML's
 * `_profile_axis` field. [description] comes from `_profile_description:` —
 * surfaced verbatim so agents can show it to the user when picking presets.
 */
data class ProfileEntry(
    val name: String,
    val axis: String,
    val description: String?,
)

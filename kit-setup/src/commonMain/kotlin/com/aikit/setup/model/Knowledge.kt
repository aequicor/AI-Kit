package com.aikit.setup.model

/** A single section of the constitution (hot tier), pointing at a markdown body. */
data class ConstitutionSection(val name: String, val includePath: String)

/** Tier 1 — `knowledge.constitution`. */
data class Constitution(
    val sections: List<ConstitutionSection>,
    val maxTokens: Int?,
)

/** Knowledge.specs / knowledge.session backend kind. */
enum class StoreKind { FILESYSTEM, MCP, HTTP, COMPOSITE, UNKNOWN }

/** Tier 3 / 4 — pluggable cold + session stores; only filesystem is fully supported here. */
data class KnowledgeStore(
    val kind: StoreKind,
    val path: String?,
    val layout: Map<String, String>,
)

/** Aggregated knowledge block. */
data class Knowledge(
    val constitution: Constitution?,
    val specs: KnowledgeStore?,
    val session: KnowledgeStore?,
)

/** A single tool declaration (MCP / LSP / built-in). */
data class ToolEntry(
    val id: String,
    val kind: String,
    val command: String?,
    val args: List<String>,
    val url: String?,
    val apiKeyEnv: String?,
    val enabled: Boolean,
)

/** A snippet of forbidden content (free text — agents pattern-match on it). */
data class Policies(
    val forbiddenPatterns: List<String>,
    val secretsDenyPatterns: List<String>,
    val testStrategy: String?,
    /** Raw `policies.slice_caps` map; surfaced into prompts as SLICE_CAPS_* vars. */
    val sliceCaps: Map<String, String>,
    /** Kit-wide hard floors per task type (`policies.model_constraints[task]`). */
    val modelConstraints: Map<String, ModelConstraint> = emptyMap(),
    /**
     * Opt-in list of optional skill IDs to emit. Optional skills (marked with
     * `<!-- aikit:optional -->` in their SKILL.md body) are skipped unless
     * their id appears here. Core skills are emitted regardless. Empty list
     * is the default — only core skills emit.
     */
    val optionalSkills: List<String> = emptyList(),
    /**
     * Extra runner-permission entries in Claude Code grammar
     * (e.g. `AskUserQuestion`, `Bash(git status:*)`, `mcp__serena__*`).
     * Generated alongside the built-in kit defaults the resolver always emits.
     * Per-runner adapters (OpenCode, Qwen Code) translate them to their own
     * permission grammar.
     */
    val permissionsAllow: List<String> = emptyList(),
    /**
     * Hard-deny rules — applied on top of [permissionsAllow]; deny wins over
     * allow at the runner level. Use for runner-level safety nets the prompt
     * cannot enforce (e.g. `Bash(git push --force *)`).
     */
    val permissionsDeny: List<String> = emptyList(),
)

/**
 * Kit-wide constraint that the resolver enforces *on top of* whatever the
 * agent requested. Agents can never soften these — they only tighten.
 */
data class ModelConstraint(
    val minTier: ModelTier?,
    val requireCapabilities: List<String>,
)

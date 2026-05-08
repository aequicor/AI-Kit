package com.aikit.setup.model

/**
 * Per-agent model selection request.
 *
 * Every nullable field means "inherit from a more general layer" — the
 * resolver merges layers as
 * `task_types[task]` → `agent.modelSelection` → `agent.byTask[task]` →
 * `agent.bySeverity[sev]` (later overrides earlier where non-null), then
 * applies `policies.model_constraints[task]` as a hard floor.
 */
data class ModelSelection(
    val needs: List<String> = emptyList(),
    val prefers: ModelTier? = null,
    val minTier: ModelTier? = null,
    val maxCost: CostHint? = null,
    /** Pin a specific [Model.id]; bypasses filtering when set. */
    val pin: String? = null,
    /** Per-task overrides keyed by `task_types[].id`. */
    val byTask: Map<String, ModelSelection> = emptyMap(),
    /** Per-severity overrides keyed by severity (`low` / `medium` / `high` / `critical`). */
    val bySeverity: Map<String, ModelSelection> = emptyMap(),
)

/**
 * Agent prompt body lookup. The map key is a model family id (`anthropic`,
 * `openai`, …) or the literal `default`. Bodies are stored as paths into the
 * embedded template tree (e.g. `prompts/CodeWriter.md`) which the generator
 * resolves at render time.
 */
data class PromptSpec(
    /** Path to the default body. */
    val defaultPath: String,
    /** Family id → body path overrides. May be empty. */
    val perFamily: Map<String, String>,
)

/**
 * One agent declared under `agents:` in the manifest.
 *
 * `mode`, `tools`, `permissions`, and `targetsOverride` carry through to the
 * adapter so per-runner frontmatter can be filled in. Any field not declared
 * in the manifest is `null` / empty list.
 */
data class Agent(
    val id: String,
    val role: String?,
    val description: String,
    val mode: String?,
    val modelSelection: ModelSelection,
    val prompt: PromptSpec,
    val tools: List<String>,
    val permissions: Map<String, String>,
    /** When non-null, restricts the agent to a subset of `render_targets`. */
    val targetsOverride: List<String>?,
)

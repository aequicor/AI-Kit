package com.aikit.setup.manifest.profile

/**
 * Per-axis whitelist of fields a profile is allowed to set. Encoded as a
 * shallow tree: at the top level, the set of allowed root keys; for keys
 * whose value is a mapping with constrained sub-keys, a nested entry pinning
 * those.
 *
 * Mirrors the `oneOf` branches in `templates/profiles/profile.schema.json`.
 * The YAML schema is the canonical contract for hand-authored profiles; this
 * registry is the runtime check the binary applies after parsing.
 *
 * If you change one, change the other in the same commit.
 */
internal data class AxisSpec(
    /** Allowed top-level keys, excluding the `_profile_*` metadata. */
    val topLevel: Set<String>,
    /**
     * For top-level keys whose value is a Mapping and whose sub-keys are
     * constrained, the set of allowed sub-keys. Top-level keys absent from
     * this map are unconstrained at depths > 1 (e.g. `tools` is a Sequence,
     * its items follow the manifest's tools schema, not the profile's).
     */
    val nested: Map<String, Set<String>>,
)

internal object AxisFieldRegistry {

    val LANGUAGE: AxisSpec = AxisSpec(
        topLevel = setOf("stack", "tools", "policies"),
        nested = mapOf(
            "stack" to setOf(
                "languages",
                "build_command",
                "compile_command",
                "lint_command",
                "test_command",
                "format_command",
                "run_command",
            ),
            "policies" to setOf("forbidden_patterns"),
        ),
    )

    val FRAMEWORK: AxisSpec = AxisSpec(
        topLevel = setOf("ui", "stack", "policies"),
        nested = mapOf(
            "stack" to setOf("frameworks"),
            "policies" to setOf("forbidden_patterns"),
            // ui is owned wholesale — no sub-key constraint
        ),
    )

    val CAPABILITY: AxisSpec = AxisSpec(
        topLevel = setOf("policies"),
        nested = mapOf(
            "policies" to setOf(
                "forbidden_patterns",
                "secrets_policy",
                "slice_caps",
                "lanes",
                "ground_truth",
                "telemetry",
                "mutation_sample",
                "test_strategy",
                "session_isolation",
                "auto_commit_per_step",
                "allow_internal_steps",
                "auto_approve",
            ),
        ),
    )

    fun forAxis(axis: ProfileAxis): AxisSpec = when (axis) {
        ProfileAxis.LANGUAGE -> LANGUAGE
        ProfileAxis.FRAMEWORK -> FRAMEWORK
        ProfileAxis.CAPABILITY -> CAPABILITY
    }
}

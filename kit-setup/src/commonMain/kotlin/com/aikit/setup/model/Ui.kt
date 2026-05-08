package com.aikit.setup.model

/**
 * Optional top-level `ui:` block — populated by framework-axis profiles
 * (or by the user's manifest directly) when the project has a user-facing
 * surface that the @Architect agent should design for.
 *
 * Absent (`null`) for backend / CLI / library projects. When null, the kit
 * skips UI-section generation in spec.md and avoids dispatching @Architect
 * with `UI_REQUIRED=true`.
 */
data class Ui(
    /** Display label, e.g. "Compose Multiplatform", "Next.js (React)". */
    val framework: String?,
    /** Free-form platform list; rendered into spec.md's UI section table. */
    val platforms: List<String>,
    /** Optional palette entries surfaced to @Architect at PLAN time. */
    val colors: List<UiColor>,
)

data class UiColor(
    val name: String,
    val hex: String?,
    val purpose: String?,
)

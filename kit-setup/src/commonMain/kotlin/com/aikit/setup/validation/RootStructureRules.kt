package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/** Top-level node must be a YAML mapping. Every other rule depends on this. */
class RootIsMappingRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        return if (RawAccess.root(manifest) == null) {
            listOf(
                ValidationError(
                    path = "",
                    code = "manifest_root_invalid",
                    message = "Manifest root must be a YAML mapping (key/value pairs).",
                ),
            )
        } else emptyList()
    }
}

/** kit_version must be present and match the SemVer-ish pattern from the schema. */
class KitVersionRule : ValidationRule {
    private val pattern = Regex("""^\d+\.\d+\.\d+$""")
    override fun check(manifest: Manifest): List<ValidationError> {
        val node = RawAccess.child(manifest.raw, "kit_version")
        val value = RawAccess.scalar(node)
        return when {
            value == null -> listOf(
                ValidationError(
                    path = "/kit_version",
                    code = "required_field_missing",
                    message = "kit_version is required.",
                    hint = "Use the SemVer string of the kit being installed (e.g. '1.0.0').",
                ),
            )
            !pattern.matches(value) -> listOf(
                ValidationError(
                    path = "/kit_version",
                    code = "kit_version_pattern_invalid",
                    message = "kit_version must match MAJOR.MINOR.PATCH.",
                ),
            )
            else -> emptyList()
        }
    }
}

/** language_code must be one of the supported values (en, ru). Defaults to en if absent. */
class LanguageCodeRule : ValidationRule {
    private val allowed = setOf("en", "ru")
    override fun check(manifest: Manifest): List<ValidationError> {
        val node = RawAccess.child(manifest.raw, "language_code") ?: return emptyList()
        val value = RawAccess.scalar(node) ?: return emptyList()
        return if (value !in allowed) listOf(
            ValidationError(
                path = "/language_code",
                code = "language_code_invalid",
                message = "language_code must be one of: ${allowed.sorted().joinToString(", ")}.",
            ),
        ) else emptyList()
    }
}

/** project.name is required (minLength 1 in schema). */
class ProjectNameRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        val name = RawAccess.scalar(RawAccess.child(RawAccess.child(manifest.raw, "project"), "name"))
        return if (name.isNullOrBlank()) listOf(
            ValidationError(
                path = "/project/name",
                code = "required_field_missing",
                message = "project.name is required.",
            ),
        ) else emptyList()
    }
}

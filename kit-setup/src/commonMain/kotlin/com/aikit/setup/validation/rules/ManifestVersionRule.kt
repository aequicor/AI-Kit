package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Asserts `manifest_version` follows the `MAJOR.MINOR.PATCH` form. Other rules
 * may eventually reject unsupported majors; this one only checks the shape so
 * a typo doesn't masquerade as an unsupported version.
 */
class ManifestVersionRule : ValidationRule {

    private val semverPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")

    override fun check(manifest: Manifest): List<ValidationError> {
        val raw = manifest.raw.field("manifest_version").stringOrNull() ?: return emptyList()
        return if (semverPattern.matches(raw)) emptyList() else listOf(
            ValidationError(
                path = "/manifest_version",
                code = "invalid_manifest_version",
                message = "manifest_version must be MAJOR.MINOR.PATCH; got `$raw`.",
            ),
        )
    }
}

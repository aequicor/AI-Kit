package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * `project.slug` is used as a machine identifier (file names, URIs, log keys),
 * so it must match `^[a-z0-9][a-z0-9-]*$`. The schema in
 * `templates/schema/kit-manifect.schema.json` enforces the same pattern; we
 * mirror it here so the binary's verify step rejects bad input before the
 * agent ever invokes generate.
 */
class ProjectSlugRule : ValidationRule {

    private val slugPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    override fun check(manifest: Manifest): List<ValidationError> {
        val project = manifest.raw.field("project") ?: return emptyList()
        val slug = project.field("slug").stringOrNull() ?: return listOf(
            ValidationError(
                path = "/project/slug",
                code = "missing_project_slug",
                message = "`project.slug` is required.",
                hint = "Add `slug: my-project` (lowercase letters, digits, dashes).",
            ),
        )
        if (!slugPattern.matches(slug)) {
            return listOf(
                ValidationError(
                    path = "/project/slug",
                    code = "invalid_project_slug",
                    message = "`project.slug` must match ^[a-z0-9][a-z0-9-]*$ (got `$slug`).",
                ),
            )
        }
        return emptyList()
    }
}

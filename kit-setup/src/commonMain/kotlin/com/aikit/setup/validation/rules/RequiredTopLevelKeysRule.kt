package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Ensures the manifest declares every top-level key the rest of the pipeline
 * assumes exists. Anything missing here is reported with the same `code` so
 * agents can iterate on a single error class.
 */
class RequiredTopLevelKeysRule : ValidationRule {

    private val required = listOf(
        "manifest_version",
        "project",
        "targets",
        "providers",
        "models",
        "agents",
    )

    override fun check(manifest: Manifest): List<ValidationError> {
        val root = manifest.raw as? RawNode.Mapping
            ?: return listOf(
                ValidationError(
                    path = "",
                    code = "manifest_root_not_mapping",
                    message = "Manifest root must be a YAML mapping.",
                ),
            )
        return required.filter { it !in root.entries }.map { key ->
            ValidationError(
                path = "/$key",
                code = "missing_required_key",
                message = "Required top-level key `$key` is missing.",
                hint = "Add `$key:` at the top level of the manifest.",
            )
        }
    }
}

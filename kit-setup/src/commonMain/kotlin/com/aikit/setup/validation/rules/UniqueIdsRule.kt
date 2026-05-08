package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Several top-level lists are keyed by id (`targets`, `providers`, `models`,
 * `agents`). Duplicate ids would silently collapse during model resolution
 * and adapter binding, so we reject them upfront with a path that points at
 * each duplicated entry.
 */
class UniqueIdsRule : ValidationRule {

    private val sections = listOf("targets", "providers", "models", "agents")

    override fun check(manifest: Manifest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (section in sections) {
            val list = manifest.raw.field(section).asList()
            val seen = mutableSetOf<String>()
            list.forEachIndexed { idx, item ->
                val id = item.field("id").stringOrNull() ?: return@forEachIndexed
                if (id in seen) {
                    errors += ValidationError(
                        path = "/$section/$idx/id",
                        code = "duplicate_id",
                        message = "Duplicate id `$id` in $section[].",
                    )
                } else {
                    seen += id
                }
            }
        }
        return errors
    }
}

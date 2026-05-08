package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.asStringList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Every entry of `render_targets[]` must reference a declared `targets[].id`.
 * Catches typos like `render_targets: [claude_code]` (underscore instead of
 * dash) before the generator silently emits nothing.
 */
class RenderTargetsExistRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val declared = manifest.raw.field("targets").asList()
            .mapNotNull { it.field("id").stringOrNull() }
            .toSet()
        val rendered = manifest.raw.field("render_targets").asStringList()
        val errors = mutableListOf<ValidationError>()
        rendered.forEachIndexed { idx, id ->
            if (id !in declared) {
                errors += ValidationError(
                    path = "/render_targets/$idx",
                    code = "unknown_render_target",
                    message = "render_targets[$idx]=`$id` is not declared in `targets[]`.",
                    hint = "Either add it to `targets:` or remove it from `render_targets:`.",
                )
            }
        }
        return errors
    }
}

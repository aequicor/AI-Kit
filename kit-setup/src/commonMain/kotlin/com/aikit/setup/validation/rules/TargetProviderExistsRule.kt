package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Every `targets[].native_provider` (other than the sentinel "any") and every
 * id in `targets[].can_use_via[]` must reference a declared provider id.
 *
 * Without this rule, a typo like `native_provider: anthropc` slips past the
 * structural pass and surfaces later as `unresolvable_model` — accurate in
 * effect but misleading in cause. Calling out the broken reference directly
 * lets the agent fix the manifest without inspecting the model resolver.
 */
class TargetProviderExistsRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val providerIds = manifest.raw.field("providers").asList()
            .mapNotNull { it.field("id").stringOrNull() }
            .toSet()
        val errors = mutableListOf<ValidationError>()
        manifest.raw.field("targets").asList().forEachIndexed { idx, target ->
            val native = target.field("native_provider").stringOrNull()
            if (native != null && native != "any" && native !in providerIds) {
                errors += ValidationError(
                    path = "/targets/$idx/native_provider",
                    code = "unknown_native_provider",
                    message = "targets[$idx].native_provider=`$native` is not declared in `providers[]`.",
                    hint = "Add a `providers[]` entry with `id: $native`, or change `native_provider` to one of the declared ids (or `any`).",
                )
            }
            target.field("can_use_via").asList().forEachIndexed { viaIdx, via ->
                val viaId = via.stringOrNull() ?: return@forEachIndexed
                if (viaId !in providerIds) {
                    errors += ValidationError(
                        path = "/targets/$idx/can_use_via/$viaIdx",
                        code = "unknown_native_provider",
                        message = "targets[$idx].can_use_via[$viaIdx]=`$viaId` is not declared in `providers[]`.",
                        hint = "Add a `providers[]` entry with `id: $viaId`, or remove it from `can_use_via`.",
                    )
                }
            }
        }
        return errors
    }
}

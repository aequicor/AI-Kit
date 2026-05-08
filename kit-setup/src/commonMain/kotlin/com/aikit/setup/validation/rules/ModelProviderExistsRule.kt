package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Every model entry must reference a declared provider id. Without this
 * check the resolver would later filter the model out for every target with
 * no diagnostic — generation would just produce "no model matched" errors
 * that don't point at the typo.
 */
class ModelProviderExistsRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val providerIds = manifest.raw.field("providers").asList()
            .mapNotNull { it.field("id").stringOrNull() }
            .toSet()
        val errors = mutableListOf<ValidationError>()
        manifest.raw.field("models").asList().forEachIndexed { idx, model ->
            val providerId = model.field("provider").stringOrNull() ?: return@forEachIndexed
            if (providerId !in providerIds) {
                errors += ValidationError(
                    path = "/models/$idx/provider",
                    code = "unknown_provider",
                    message = "models[$idx].provider=`$providerId` is not declared in `providers[]`.",
                )
            }
        }
        return errors
    }
}

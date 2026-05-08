package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/** When provider is present, name/base_url/api_key_env are all required. */
class ProviderRequiredFieldsRule : ValidationRule {
    private val keys = listOf("name", "base_url", "api_key_env")
    override fun check(manifest: Manifest): List<ValidationError> {
        val provider = RawAccess.child(manifest.raw, "provider")
        if (!RawAccess.isPresent(provider)) return emptyList()
        return keys.mapNotNull { key ->
            val value = RawAccess.scalar(RawAccess.child(provider, key))
            if (value.isNullOrBlank()) ValidationError(
                path = "/provider/$key",
                code = "provider_field_missing",
                message = "provider.$key is required.",
            ) else null
        }
    }
}

/** When models is present, default/coder/reviewer are required. */
class ModelsRequiredFieldsRule : ValidationRule {
    private val keys = listOf("default", "coder", "reviewer")
    override fun check(manifest: Manifest): List<ValidationError> {
        val models = RawAccess.child(manifest.raw, "models")
        if (!RawAccess.isPresent(models)) return emptyList()
        return keys.mapNotNull { key ->
            val value = RawAccess.scalar(RawAccess.child(models, key))
            if (value.isNullOrBlank()) ValidationError(
                path = "/models/$key",
                code = "models_field_missing",
                message = "models.$key is required.",
            ) else null
        }
    }
}

/** Same shape applies to claude_code.models. */
class ClaudeCodeModelsRequiredFieldsRule : ValidationRule {
    private val keys = listOf("default", "coder", "reviewer")
    override fun check(manifest: Manifest): List<ValidationError> {
        val models = RawAccess.child(RawAccess.child(manifest.raw, "claude_code"), "models")
        if (!RawAccess.isPresent(models)) return emptyList()
        return keys.mapNotNull { key ->
            val value = RawAccess.scalar(RawAccess.child(models, key))
            if (value.isNullOrBlank()) ValidationError(
                path = "/claude_code/models/$key",
                code = "claude_models_field_missing",
                message = "claude_code.models.$key is required.",
            ) else null
        }
    }
}

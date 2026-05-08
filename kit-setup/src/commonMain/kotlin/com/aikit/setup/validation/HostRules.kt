package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * `hosts` must be a non-empty array of unique values drawn from
 * `{opencode, claude-code}`. The kit only knows how to render those two.
 */
class HostsListRule : ValidationRule {
    private val allowed = setOf("opencode", "claude-code")
    override fun check(manifest: Manifest): List<ValidationError> {
        val node = RawAccess.child(manifest.raw, "hosts")
        if (node == null) {
            return listOf(
                ValidationError(
                    path = "/hosts",
                    code = "required_field_missing",
                    message = "hosts is required (at least one of: ${allowed.sorted().joinToString(", ")}).",
                ),
            )
        }
        val seq = RawAccess.sequence(node) ?: return listOf(
            ValidationError(
                path = "/hosts",
                code = "type_invalid",
                message = "hosts must be a YAML list, e.g. [opencode, claude-code].",
            ),
        )
        val errors = mutableListOf<ValidationError>()
        if (seq.items.isEmpty()) {
            errors += ValidationError(
                path = "/hosts",
                code = "hosts_empty",
                message = "hosts must contain at least one host name.",
            )
        }
        val seen = mutableSetOf<String>()
        seq.items.forEachIndexed { index, item ->
            val value = RawAccess.scalar(item)
            if (value == null) {
                errors += ValidationError(
                    path = "/hosts/$index",
                    code = "type_invalid",
                    message = "hosts[$index] must be a string.",
                )
                return@forEachIndexed
            }
            if (value !in allowed) {
                errors += ValidationError(
                    path = "/hosts/$index",
                    code = "hosts_enum_invalid",
                    message = "hosts[$index]='$value' is not one of: ${allowed.sorted().joinToString(", ")}.",
                )
            }
            if (!seen.add(value)) {
                errors += ValidationError(
                    path = "/hosts/$index",
                    code = "hosts_duplicate",
                    message = "hosts[$index]='$value' is duplicated; entries must be unique.",
                )
            }
        }
        return errors
    }
}

/** When `opencode` ∈ hosts, `provider` and `models` are required. */
class OpencodeRequiresProviderAndModelsRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        val hosts = RawAccess.stringSequenceValues(RawAccess.child(manifest.raw, "hosts"))
        if ("opencode" !in hosts) return emptyList()
        val errors = mutableListOf<ValidationError>()
        if (!RawAccess.isPresent(RawAccess.child(manifest.raw, "provider"))) {
            errors += ValidationError(
                path = "/provider",
                code = "provider_required_when_opencode",
                message = "provider is required when 'opencode' is in hosts.",
            )
        }
        if (!RawAccess.isPresent(RawAccess.child(manifest.raw, "models"))) {
            errors += ValidationError(
                path = "/models",
                code = "models_required_when_opencode",
                message = "models is required when 'opencode' is in hosts.",
            )
        }
        return errors
    }
}

/** When `claude-code` ∈ hosts, `claude_code.models` is required. */
class ClaudeCodeRequiresModelsRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        val hosts = RawAccess.stringSequenceValues(RawAccess.child(manifest.raw, "hosts"))
        if ("claude-code" !in hosts) return emptyList()
        val cc = RawAccess.child(manifest.raw, "claude_code")
        if (!RawAccess.isPresent(cc)) {
            return listOf(
                ValidationError(
                    path = "/claude_code",
                    code = "claude_code_required_when_claude_code_host",
                    message = "claude_code is required when 'claude-code' is in hosts.",
                ),
            )
        }
        val models = RawAccess.child(cc, "models")
        if (!RawAccess.isPresent(models)) {
            return listOf(
                ValidationError(
                    path = "/claude_code/models",
                    code = "claude_models_field_missing",
                    message = "claude_code.models is required when 'claude-code' is in hosts.",
                ),
            )
        }
        return emptyList()
    }
}

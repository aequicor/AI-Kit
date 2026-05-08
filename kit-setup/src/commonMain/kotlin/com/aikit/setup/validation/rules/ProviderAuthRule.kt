package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Validates `providers[].auth` and its interaction with `api_key_env`.
 *
 *  - `auth` must be one of `api_key` | `subscription` | `none` when present.
 *  - When `auth` is `api_key` (or omitted, since that's the default),
 *    `api_key_env` must be set — otherwise the runner has no token to use.
 *  - When `auth` is `subscription` or `none`, `api_key_env` is unused; we
 *    do not error if present (a manifest authored for both modes is fine),
 *    but we don't require it.
 *
 * Stable error codes:
 *  - `unknown_provider_auth`     — `auth` value not in the enum.
 *  - `missing_api_key_env`       — `auth: api_key` (default) without `api_key_env`.
 */
class ProviderAuthRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        manifest.raw.field("providers").asList().forEachIndexed { idx, provider ->
            val rawAuth = provider.field("auth")?.stringOrNull()
            val apiKeyEnv = provider.field("api_key_env")?.stringOrNull()
            val providerId = provider.field("id")?.stringOrNull() ?: "(unknown)"

            val effectiveAuth = when (rawAuth?.lowercase()) {
                null -> "api_key"
                "api_key", "api-key", "apikey" -> "api_key"
                "subscription", "runner", "runner_managed", "runner-managed" -> "subscription"
                "none" -> "none"
                else -> {
                    errors += ValidationError(
                        path = "/providers/$idx/auth",
                        code = "unknown_provider_auth",
                        message = "providers[$idx].auth=`$rawAuth` is not one of `api_key`, `subscription`, `none`.",
                        hint = "Use `subscription` for runners signed in via account login (Claude Code, Cursor, Qwen Code), `none` for local backends like Ollama, or `api_key` (the default) with `api_key_env`.",
                    )
                    return@forEachIndexed
                }
            }

            if (effectiveAuth == "api_key" && apiKeyEnv.isNullOrBlank()) {
                errors += ValidationError(
                    path = "/providers/$idx/api_key_env",
                    code = "missing_api_key_env",
                    message = "providers[$idx] (`$providerId`) uses `auth: api_key` but no `api_key_env` is set.",
                    hint = "Set `api_key_env` to the env-var name holding the key, or change `auth` to `subscription` (runner-managed login) or `none` (local backend).",
                )
            }
        }
        return errors
    }
}

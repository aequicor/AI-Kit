package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * Refuse to accept a manifest where `*_api_key_env` looks like a real API key
 * rather than the **name** of an environment variable. Mirrors the security
 * gate from PHASE 2.4 of the setup prompt — agents and humans both need to
 * be told off loudly the moment a literal credential lands in YAML.
 */
class ApiKeyEnvLooksLikeLiteralRule : ValidationRule {

    private val literalPrefixes = Regex("""^(sk|ghp|ghs|glpat|xoxp|xoxb)-""")
    private val awsAccessKey = Regex("""^AKIA[0-9A-Z]{16}$""")

    override fun check(manifest: Manifest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val candidates = listOf(
            "/provider/api_key_env" to RawAccess.child(RawAccess.child(manifest.raw, "provider"), "api_key_env"),
            "/mcp/context7/api_key_env" to
                RawAccess.child(RawAccess.child(RawAccess.child(manifest.raw, "mcp"), "context7"), "api_key_env"),
        )
        for ((path, node) in candidates) {
            val value = RawAccess.scalar(node) ?: continue
            if (looksLikeLiteralKey(value)) {
                errors += ValidationError(
                    path = path,
                    code = "api_key_literal_detected",
                    message = "$path looks like a real API key. Use the env-var NAME (e.g. ROUTERAI_OPENCODE), never the value itself.",
                    hint = "Set the value with `export <NAME>=<key>` in your shell before launching the agent host.",
                )
            }
        }
        return errors
    }

    private fun looksLikeLiteralKey(value: String): Boolean {
        if (literalPrefixes.containsMatchIn(value)) return true
        if (awsAccessKey.matches(value)) return true
        if (value.length >= 32 && value.any { it.isDigit() } && value.any { it.isLetter() } && value.any { !it.isLetterOrDigit() }) {
            return true
        }
        return false
    }
}

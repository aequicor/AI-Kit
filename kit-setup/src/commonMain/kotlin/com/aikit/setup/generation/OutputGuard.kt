package com.aikit.setup.generation

/**
 * Pre-write check that scans rendered content against the manifest's
 * `policies.secrets_policy.deny_patterns` regexes.
 *
 * The intent is defense-in-depth: a prompt body, snippet, or substituted
 * variable that ends up carrying a literal API key (e.g. someone pastes a
 * `sk-...` token into a markdown file) gets refused at write time rather
 * than committed to disk and possibly to git.
 *
 * Patterns are compiled once at generator construction. Patterns that fail
 * to compile (malformed user input) are dropped silently — config quality
 * is a separate concern; this class only enforces the well-formed subset.
 */
class OutputGuard(rawPatterns: List<String>) {

    private val patterns: List<Regex> = rawPatterns.mapNotNull { p ->
        try {
            Regex(p)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns the source patterns of every regex that matched [content].
     * Empty list = content is clean — caller proceeds with the write.
     */
    fun scan(content: String): List<String> =
        patterns.filter { it.containsMatchIn(content) }.map { it.pattern }
}

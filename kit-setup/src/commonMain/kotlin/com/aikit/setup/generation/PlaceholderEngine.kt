package com.aikit.setup.generation

import com.aikit.setup.templates.TemplateRegistry

/**
 * Substitutes `{{NAME}}` placeholders in template bodies. Three-tier resolution
 * keeps templates DRY:
 *
 *  - **conditional blocks** (`{{#if NAME}}…{{/if}}` or `{{#if snippet:NAME}}…{{/if}}`)
 *    keep their inner content only when the condition is "truthy" — for a
 *    variable, the key is in [variables] and its value is non-blank; for a
 *    snippet, the file resolves and its content is non-blank after variable
 *    substitution. False blocks (and one trailing newline after each marker)
 *    are removed so wrappers don't leave dangling tags or empty headers.
 *    Nested blocks are supported.
 *  - **simple variables** (e.g. `{{PROJECT_NAME}}`) are replaced by literal
 *    strings the generator computes per render context.
 *  - **snippet inclusions** (`{{snippet:project_context}}`) expand into the
 *    contents of `<dialect>/snippets/<name>.md` if present, otherwise
 *    `<shared>/snippets/<name>.md`. Snippets are themselves substituted with
 *    the same variables before being inlined, so a snippet can use any of
 *    the simple placeholders too.
 *
 * Unknown variables are left as-is; that's deliberate — the surrounding
 * generator may run multiple substitution passes, and silently dropping a
 * `{{X}}` would mask bugs in template authors' hands.
 */
class PlaceholderEngine(
    private val templates: TemplateRegistry,
    private val dialectPath: String,
    private val sharedPath: String?,
) {

    /**
     * Returns [body] with every `{{#if …}}…{{/if}}` resolved, every
     * `{{snippet:NAME}}` expanded, and every key in [variables] substituted
     * into matching `{{KEY}}` tokens.
     */
    fun render(body: String, variables: Map<String, String>): String {
        val conditioned = processConditionals(body, variables)
        val expanded = expandSnippets(conditioned, variables)
        return substituteVariables(expanded, variables)
    }

    private fun processConditionals(body: String, variables: Map<String, String>): String {
        var current = body
        // Innermost-first: the first `{{/if}}` always matches the last `{{#if}}`
        // before it (any nested open would have a closer close). Resolve that
        // pair, restart. The cap prevents pathological inputs from hanging.
        repeat(MAX_CONDITIONAL_PASSES) {
            val pair = findInnermostBlock(current) ?: return current
            val (open, closeStart) = pair
            var inner = current.substring(open.contentStart, closeStart)
            if (inner.startsWith("\n")) inner = inner.substring(1)
            val closeEnd = closeStart + IF_CLOSE.length
            val replaceEnd = if (closeEnd < current.length && current[closeEnd] == '\n') closeEnd + 1 else closeEnd
            val replacement = if (isTruthy(open.condition, variables)) inner else ""
            current = current.substring(0, open.openStart) + replacement + current.substring(replaceEnd)
        }
        return current
    }

    private data class IfOpen(val openStart: Int, val contentStart: Int, val condition: String)

    private fun findInnermostBlock(text: String): Pair<IfOpen, Int>? {
        val closeIdx = text.indexOf(IF_CLOSE)
        if (closeIdx < 0) return null
        var latest: IfOpen? = null
        for (match in IF_OPEN.findAll(text)) {
            if (match.range.first >= closeIdx) break
            latest = IfOpen(match.range.first, match.range.last + 1, match.groupValues[1])
        }
        return latest?.let { it to closeIdx }
    }

    private fun isTruthy(condition: String, variables: Map<String, String>): Boolean {
        if (condition.startsWith("snippet:")) {
            val name = condition.removePrefix("snippet:")
            val resolved = readSnippet(name) ?: return false
            return substituteVariables(resolved, variables).isNotBlank()
        }
        val value = variables[condition] ?: return false
        return value.isNotBlank()
    }

    private fun expandSnippets(body: String, variables: Map<String, String>): String {
        // Simple repeated find-and-replace; nested snippets are supported up to
        // a small depth to prevent runaway loops in the face of cyclic refs.
        // Process conditionals inside the snippet body before inlining — the
        // outer-pass `processConditionals` already ran, so without this step
        // any `{{#if}}` blocks inside a snippet would survive into the output.
        var current = body
        repeat(8) {
            val pattern = Regex("\\{\\{snippet:([A-Za-z0-9_-]+)\\}\\}")
            val match = pattern.find(current) ?: return current
            val name = match.groupValues[1]
            val resolved = readSnippet(name) ?: ""
            val conditioned = processConditionals(resolved, variables)
            val rendered = substituteVariables(conditioned, variables)
            current = current.replaceRange(match.range, rendered)
        }
        return current
    }

    private fun readSnippet(name: String): String? {
        val dialectSnippet = templates.read("$dialectPath/snippets/$name.md")
        if (dialectSnippet != null) return dialectSnippet
        val shared = sharedPath?.let { normalize(it) }
        if (shared != null) {
            return templates.read("$shared/snippets/$name.md")
        }
        return null
    }

    private fun substituteVariables(text: String, variables: Map<String, String>): String {
        if (variables.isEmpty()) return text
        var current = text
        for ((key, value) in variables) {
            current = current.replace("{{$key}}", value)
        }
        return current
    }

    private fun normalize(p: String): String {
        var s = p.trim()
        while (s.startsWith("./")) s = s.removePrefix("./")
        return s.trimEnd('/')
    }

    private companion object {
        const val IF_CLOSE = "{{/if}}"
        const val MAX_CONDITIONAL_PASSES = 64
        val IF_OPEN = Regex("\\{\\{#if\\s+([A-Za-z0-9_:-]+)\\s*}}")
    }
}

package com.aikit.setup.generation

import com.aikit.setup.templates.TemplateRegistry

/**
 * Substitutes `{{NAME}}` placeholders in template bodies. Two-tier resolution
 * keeps templates DRY:
 *
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
     * Returns [body] with every `{{snippet:NAME}}` expanded and every key in
     * [variables] substituted into matching `{{KEY}}` tokens.
     */
    fun render(body: String, variables: Map<String, String>): String {
        val expanded = expandSnippets(body, variables)
        return substituteVariables(expanded, variables)
    }

    private fun expandSnippets(body: String, variables: Map<String, String>): String {
        // Simple repeated find-and-replace; nested snippets are supported up to
        // a small depth to prevent runaway loops in the face of cyclic refs.
        var current = body
        repeat(8) {
            val pattern = Regex("\\{\\{snippet:([A-Za-z0-9_-]+)\\}\\}")
            val match = pattern.find(current) ?: return current
            val name = match.groupValues[1]
            val resolved = readSnippet(name) ?: ""
            val rendered = substituteVariables(resolved, variables)
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
}

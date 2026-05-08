package com.aikit.setup.render

import com.aikit.setup.embedded.EmbeddedTemplates

/**
 * Two-pass template engine driving the kit generator.
 *
 * Pass 1 — `{{INCLUDE: <path>}}` directives. Paths are resolved relative to
 * the embedded `kit/` directory (so `_shared/agents/Main.body.md.template`
 * looks up `kit/_shared/agents/Main.body.md.template`). Includes are
 * resolved recursively up to [MAX_INCLUDE_DEPTH] to catch cycles.
 *
 * Pass 2 — `{{VAR}}` substitution. Each `{{NAME}}` is replaced with the
 * matching value from the substitution map, or left intact if the map has
 * no entry (so the post-render scan can flag it).
 *
 * Both passes operate on plain strings; the renderer never reads the
 * filesystem — every source file must be present in the embedded map.
 */
class TemplateRenderer {

    companion object {
        const val MAX_INCLUDE_DEPTH: Int = 5
    }

    fun render(source: String, vars: Map<String, String>): RenderResult {
        val expanded = resolveIncludes(source, depth = 0, stack = mutableListOf())
        val substituted = substituteVars(expanded, vars)
        val unresolved = findUnresolved(substituted)
        return RenderResult(substituted, unresolved)
    }

    private fun resolveIncludes(content: String, depth: Int, stack: MutableList<String>): String {
        if (depth > MAX_INCLUDE_DEPTH) {
            error("INCLUDE recursion exceeded depth $MAX_INCLUDE_DEPTH (chain: ${stack.joinToString(" -> ")})")
        }
        val pattern = Regex("""\{\{INCLUDE:\s*([^}\n]+?)\s*\}\}""")
        return pattern.replace(content) { match ->
            val rel = match.groupValues[1]
            if (rel.contains("..") || rel.startsWith("/")) {
                error("Malformed INCLUDE path: '$rel' (no '..' or absolute paths allowed)")
            }
            val key = "kit/$rel"
            val included = EmbeddedTemplates.files[key]
                ?: error("INCLUDE target not embedded: 'kit/$rel'")
            stack.add(rel)
            try {
                resolveIncludes(included, depth + 1, stack)
            } finally {
                stack.removeAt(stack.size - 1)
            }
        }
    }

    private fun substituteVars(content: String, vars: Map<String, String>): String {
        val pattern = Regex("""\{\{([A-Z][A-Z0-9_]*)\}\}""")
        return pattern.replace(content) { match ->
            val name = match.groupValues[1]
            vars[name] ?: match.value
        }
    }

    private fun findUnresolved(content: String): List<String> {
        val placeholders = Regex("""\{\{([A-Z][A-Z0-9_]*)\}\}""").findAll(content)
            .map { it.groupValues[1] }
            .toList()
        val includes = Regex("""\{\{INCLUDE:""").containsMatchIn(content)
        return if (includes) placeholders + "_INCLUDE_RESIDUAL" else placeholders
    }
}

/**
 * Outcome of a [TemplateRenderer.render] call.
 *
 * [unresolved] lists every placeholder name still present after both passes.
 * The caller decides whether that's a hard error (a missing required
 * variable) or expected (a comment in the source that mentions `{{VAR}}`
 * literally). In the kit generator it's escalated to a generation error.
 */
data class RenderResult(val text: String, val unresolved: List<String>)

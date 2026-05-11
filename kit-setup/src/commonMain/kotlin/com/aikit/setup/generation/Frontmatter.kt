package com.aikit.setup.generation

/**
 * Builds the YAML frontmatter block prepended to agent / skill / command
 * markdown files. Templates from adapter packages already use `key: "{{VAR}}"`
 * shape, so this helper just runs placeholder substitution and brackets the
 * result with `---` lines.
 *
 * After substitution, lines whose value collapsed to an empty string (e.g.
 * `tools: ""` when an agent declared no tools) are dropped. Empty-string
 * values in Claude Code / OpenCode frontmatter are interpreted as
 * "explicitly nothing allowed" rather than "use the default" — the result
 * is commands or agents that silently lose access to every tool. The author
 * almost always meant "omit the key"; making the renderer omit the line is
 * cheaper than asking every template to guard each placeholder.
 *
 * Only lines that EXACTLY match `<key>: ""` (single or double quotes,
 * optional whitespace) are dropped. Multi-line values and list-shaped
 * frontmatter pass through unchanged.
 */
object Frontmatter {

    private val emptyScalarLine = Regex("""^\s*[A-Za-z0-9_-]+\s*:\s*(""|'')\s*$""")

    fun render(template: String, variables: Map<String, String>): String {
        var body = template
        for ((k, v) in variables) {
            body = body.replace("{{$k}}", v)
        }
        val cleaned = body.lineSequence()
            .filterNot { emptyScalarLine.matches(it) }
            .joinToString("\n")
        return buildString {
            append("---\n")
            append(cleaned.trimEnd())
            append("\n---\n")
        }
    }
}

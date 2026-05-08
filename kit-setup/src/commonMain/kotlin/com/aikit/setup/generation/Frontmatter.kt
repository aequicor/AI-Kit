package com.aikit.setup.generation

/**
 * Builds the YAML frontmatter block prepended to agent / skill / command
 * markdown files. Templates from adapter packages already use `key: "{{VAR}}"`
 * shape, so this helper just runs placeholder substitution and brackets the
 * result with `---` lines.
 */
object Frontmatter {

    fun render(template: String, variables: Map<String, String>): String {
        var body = template
        for ((k, v) in variables) {
            body = body.replace("{{$k}}", v)
        }
        return buildString {
            append("---\n")
            append(body.trimEnd())
            append("\n---\n")
        }
    }
}

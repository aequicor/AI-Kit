package com.aikit.setup.generation

/**
 * Decomposed view of a SKILL.md body.
 *
 * The templates/skills/<id>/SKILL.md files don't carry YAML frontmatter —
 * they organise content with markdown sections (`# When to invoke`,
 * `# Procedure`, `# Output`). The dialect wrapper expects each of those as a
 * separate placeholder ({{SKILL_TRIGGERS}}, {{BODY}}, {{SKILL_OUTPUT_FORMAT}}),
 * so the renderer pulls the sections out and feeds them in individually
 * instead of dumping the whole body inside `<procedure>`.
 */
data class SkillSections(
    val description: String,
    val triggers: String,
    val procedure: String,
    val outputFormat: String,
)

/**
 * Splits [body] into [SkillSections] using a tiny markdown sectioniser:
 *
 *  - the description is the first non-blank line that precedes any `#`
 *    heading (often the one-liner that opens the file)
 *  - everything from one `#`-heading up to the next is the section body
 *  - heading titles are matched case-insensitively, with a small alias set
 *    (`when to invoke` / `output` / `output format` / `procedure`)
 *
 * Sections that don't appear in the file collapse to `""` — the wrapper
 * placeholder then renders empty rather than raising. The procedure
 * defaults to "everything not assigned elsewhere" so unstructured skills
 * still produce something sensible.
 */
fun parseSkillSections(body: String): SkillSections {
    val lines = body.lines()
    val sections = linkedMapOf<String, StringBuilder>()
    val headerRegex = Regex("^#{1,6}\\s+(.+?)\\s*$")
    var currentTitle: String? = null
    var currentBuf: StringBuilder = StringBuilder()
    var preheaderLines = mutableListOf<String>()

    for (line in lines) {
        val match = headerRegex.matchEntire(line)
        if (match != null) {
            // Close out the previous section (or pre-header content).
            if (currentTitle != null) {
                sections[currentTitle.lowercase()] = currentBuf
            }
            currentTitle = match.groupValues[1].trim()
            currentBuf = StringBuilder()
            continue
        }
        if (currentTitle == null) {
            preheaderLines += line
        } else {
            currentBuf.appendLine(line)
        }
    }
    if (currentTitle != null) {
        sections[currentTitle.lowercase()] = currentBuf
    }

    val description = preheaderLines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    fun pickSection(vararg aliases: String): String {
        for (a in aliases) {
            sections[a.lowercase()]?.let { return it.toString().trim() }
        }
        return ""
    }

    val triggers = pickSection("when to invoke", "triggers", "when")
    val procedure = pickSection("procedure", "steps", "how", "how to")
    val outputFormat = pickSection("output", "output format")

    return SkillSections(
        description = description,
        triggers = triggers,
        procedure = procedure,
        outputFormat = outputFormat,
    )
}

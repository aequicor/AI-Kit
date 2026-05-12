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
 *    heading (often the one-liner that opens the file). Lines that are
 *    entirely an HTML comment (`<!-- ... -->`) are skipped so the optional
 *    marker (see [isOptionalSkill]) does not leak into the description.
 *  - only **level-1** (`#`) headings start a new section; `##` and deeper
 *    are treated as content within the current section so authors can
 *    structure procedures with sub-headings without breaking the parse
 *  - heading titles are matched case-insensitively, with a small alias set
 *    (`when to invoke` / `output` / `output format` / `procedure`)
 *  - lines inside a fenced code block (delimited by three-or-more backticks
 *    or tildes) are treated as opaque content; a `#` line inside such a
 *    block is NOT a heading. Without this, a SKILL whose procedure carries
 *    a markdown template wrapped in ```` ```markdown ```` (e.g. the plan
 *    artifact's `# <Task title>` line) would truncate at the first fenced
 *    `#` and leak the rest into a phantom section.
 *
 * Sections that don't appear in the file collapse to `""` — the wrapper
 * placeholder then renders empty rather than raising. The procedure
 * defaults to "everything not assigned elsewhere" so unstructured skills
 * still produce something sensible.
 */
fun parseSkillSections(body: String): SkillSections {
    val lines = body.lines()
    val sections = linkedMapOf<String, StringBuilder>()
    val headerRegex = Regex("^#\\s+(.+?)\\s*$")
    val htmlCommentLine = Regex("^\\s*<!--.*-->\\s*$")
    val fenceRegex = Regex("^(`{3,}|~{3,}).*$")
    var inFence = false
    var currentTitle: String? = null
    var currentBuf: StringBuilder = StringBuilder()
    var preheaderLines = mutableListOf<String>()

    for (line in lines) {
        if (fenceRegex.matches(line)) {
            inFence = !inFence
            if (currentTitle == null) {
                preheaderLines += line
            } else {
                currentBuf.appendLine(line)
            }
            continue
        }
        if (!inFence) {
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

    val description = preheaderLines
        .firstOrNull { it.isNotBlank() && !htmlCommentLine.matches(it) }
        ?.trim()
        .orEmpty()

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

/**
 * True when [body] declares the skill as optional via the `<!-- aikit:optional -->`
 * HTML comment marker. Optional skills are skipped by the generator unless their
 * id appears in `policies.optional_skills` in the manifest — see
 * [com.aikit.setup.generation.DefaultKitGenerator.renderSkills].
 *
 * The marker can appear anywhere in the file but conventionally lands on the
 * first line, before the description. Match is permissive on surrounding
 * whitespace; `aikit:optional` is the only payload form accepted (so
 * adjacent comments like `<!-- TODO -->` don't accidentally opt in).
 */
fun isOptionalSkill(body: String): Boolean =
    OPTIONAL_MARKER_REGEX.containsMatchIn(body)

private val OPTIONAL_MARKER_REGEX = Regex("<!--\\s*aikit:optional\\s*-->")

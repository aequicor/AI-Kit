package com.aikit.setup.generation

/**
 * Default [ConditionalRenderer] implementation. Pure — no I/O, no template
 * registry access, no side effects. Construct once and reuse across renders.
 *
 * **Grammar (MVP):**
 * ```
 * <open>   ::= '{{#if'    SP <cond> SP? '}}'
 *            | '{{#unless' SP <cond> SP? '}}'
 * <close>  ::= '{{/if}}' | '{{/unless}}'
 * <cond>   ::= <var> | <var> SP? '==' SP? '"' <literal> '"'
 * <var>    ::= 'cap.' <ident> | 'target.id' | 'family'
 * ```
 *
 * **Structural rules:**
 *  - Open and close tags must each occupy their own line (only surrounding
 *    whitespace allowed). An inline tag whose condition is in our grammar
 *    fails with `template_conditional_invalid_syntax` — inline tags whose
 *    condition belongs to [PlaceholderEngine]'s namespace are passed through
 *    untouched.
 *  - Nesting is not supported in this MVP — a nested `{{#if}}`/`{{#unless}}`
 *    inside an already-open block fails with `template_conditional_nested`.
 *  - Block bodies are emitted verbatim when kept; no trimming.
 *  - When dropped, the entire block (open through close lines) is removed
 *    without leaving artifacts. Pre-existing blank lines around the block
 *    are preserved as-written, so `{{#if A}}…{{/if}} <blank> {{#if B}}…{{/if}}`
 *    with both dropped keeps the single blank line between them.
 *
 * **Coexistence with [PlaceholderEngine]:** any `{{#if <cond>}}` whose
 * `<cond>` doesn't start with `cap.`, `target.`, or `family` is left
 * untouched — the placeholder engine will see and process it later.
 * `{{#unless …}}` has no counterpart in the placeholder engine, so it is
 * always claimed; an unrecognized condition under `#unless` is a fatal
 * `template_conditional_invalid_syntax`.
 */
class DefaultConditionalRenderer : ConditionalRenderer {

    override fun render(template: String, context: RenderContext): String {
        // Normalize CRLF so the line-based scan doesn't carry stray \r into
        // trimmed-line comparisons. Final output uses LF — matches the
        // template tree's checked-in line endings.
        val src = template.replace("\r\n", "\n")
        val lines = src.split("\n")
        val out = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val anchored = OPEN_ANCHORED.matchEntire(line)
            if (anchored != null) {
                val form = anchored.groupValues[1]
                val cond = anchored.groupValues[2]
                if (isOurCondition(cond)) {
                    i = processBlock(lines, i, form, cond, context, out)
                    continue
                }
                if (form == "unless") {
                    // `{{#unless}}` has no counterpart in PlaceholderEngine,
                    // so any condition that's not in our domain is unreachable
                    // — surface it now rather than leaking the raw tag into
                    // the rendered output.
                    throw ConditionalRenderException(
                        code = "template_conditional_invalid_syntax",
                        path = "",
                        message = "{{#unless $cond}} — condition is not in the context " +
                            "namespace (`cap.<key>`, `target.id`, `family`); {{#unless}} " +
                            "is reserved for context-aware conditionals",
                    )
                }
                // Recognized `{{#if}}` tag with a non-domain condition (e.g.
                // `{{#if TOOLS_LIST}}` or `{{#if snippet:NAME}}`) — leave for
                // PlaceholderEngine to handle in its later pass.
                out += line
                i++
                continue
            }
            // No anchored open. Guard against inline misuse: if a tag
            // appears mid-line and the condition is ours, the author broke
            // the "tag on its own line" rule and we surface it now.
            val inlineOpen = OPEN_INLINE.find(line)
            if (inlineOpen != null && isOurCondition(inlineOpen.groupValues[2])) {
                throw ConditionalRenderException(
                    code = "template_conditional_invalid_syntax",
                    path = "",
                    message = "{{#${inlineOpen.groupValues[1]} ${inlineOpen.groupValues[2]}}} " +
                        "tag must be on its own line (got: \"${line.trim()}\")",
                )
            }
            // Anchored `{{/unless}}` outside any of our blocks would only
            // happen if the matching open never existed (or wasn't ours).
            // Surface it — unless tags are exclusively in our namespace.
            val anchoredClose = CLOSE_ANCHORED.matchEntire(line)
            if (anchoredClose != null && anchoredClose.groupValues[1] == "unless") {
                throw ConditionalRenderException(
                    code = "template_conditional_mismatched",
                    path = "",
                    message = "stray {{/unless}} without matching {{#unless …}}",
                )
            }
            out += line
            i++
        }

        return out.joinToString("\n")
    }

    /**
     * Scans from the open-tag line at [openIdx] to the matching close, and
     * either emits the inner body lines (when kept) or nothing (when
     * dropped). Returns the next line index to resume scanning from.
     *
     * Throws [ConditionalRenderException] on unclosed blocks, mismatched
     * forms, nested ours-blocks, or unknown variables.
     */
    private fun processBlock(
        lines: List<String>,
        openIdx: Int,
        form: String,
        cond: String,
        context: RenderContext,
        out: MutableList<String>,
    ): Int {
        var j = openIdx + 1
        while (j < lines.size) {
            val l = lines[j]
            val nestedOpen = OPEN_ANCHORED.matchEntire(l)
            if (nestedOpen != null && isOurCondition(nestedOpen.groupValues[2])) {
                throw ConditionalRenderException(
                    code = "template_conditional_nested",
                    path = "",
                    message = "nested {{#${nestedOpen.groupValues[1]} ${nestedOpen.groupValues[2]}}} " +
                        "inside {{#$form $cond}} — nesting is not supported in this MVP",
                )
            }
            val close = CLOSE_ANCHORED.matchEntire(l)
            if (close != null && isOurCloseForm(close.groupValues[1], form)) {
                if (close.groupValues[1] != form) {
                    throw ConditionalRenderException(
                        code = "template_conditional_mismatched",
                        path = "",
                        message = "{{#$form $cond}} closed by {{/${close.groupValues[1]}}}",
                    )
                }
                val keep = evaluate(cond, context).let { if (form == "unless") !it else it }
                if (keep) {
                    for (k in (openIdx + 1) until j) out += lines[k]
                }
                return j + 1
            }
            j++
        }
        throw ConditionalRenderException(
            code = "template_conditional_unclosed",
            path = "",
            message = "{{#$form $cond}} not closed before end of template",
        )
    }

    /**
     * True iff the close-form `{{/if}}` or `{{/unless}}` belongs to *this*
     * open block. A `{{/if}}` always closes whichever open we're tracking
     * (mismatch surfaces later); a `{{/unless}}` only closes a `{{#unless}}`
     * — a stray one for a different opener would be claimed by us anyway
     * because `{{/unless}}` has no other consumer in the codebase.
     */
    private fun isOurCloseForm(closeForm: String, openForm: String): Boolean =
        closeForm == openForm ||
            // mismatched pair (e.g. {{#if}} … {{/unless}}) — still ours, so
            // we can report it as `template_conditional_mismatched` rather
            // than silently leaking it.
            (closeForm == "if" && openForm == "unless") ||
            (closeForm == "unless" && openForm == "if")

    /**
     * True iff [cond] is in this renderer's namespace — anything starting
     * with `cap.`, `target.`, or `family`. Lookalikes (`Cap.x`, `targets.id`)
     * fall through to [PlaceholderEngine] untouched.
     */
    private fun isOurCondition(cond: String): Boolean {
        val trimmed = cond.trim()
        return trimmed.startsWith("cap.") ||
            trimmed.startsWith("target.") ||
            trimmed == "family" ||
            trimmed.startsWith("family ") ||
            trimmed.startsWith("family\t")
    }

    /**
     * Resolves a condition under [context] to a boolean. Throws on unknown
     * capability keys, unrecognized `target.*` accessors, or syntax errors.
     */
    private fun evaluate(cond: String, context: RenderContext): Boolean {
        val match = CONDITION.matchEntire(cond.trim())
            ?: throw ConditionalRenderException(
                code = "template_conditional_invalid_syntax",
                path = "",
                message = "unsupported condition: \"$cond\" (expected `cap.<key>`, " +
                    "`target.id`, `family`, optionally followed by `== \"literal\"`)",
            )
        val variable = match.groupValues[1]
        val literal: String? = if (match.groupValues[2].isNotEmpty()) match.groupValues[3] else null
        val value = resolveVariable(variable, context)
        return if (literal == null) {
            // Standalone form: boolean truthiness. `cap.X` resolves to
            // "true"/"false" so it folds into this rule cleanly; non-empty
            // non-"false" strings are truthy (for target.id and family).
            value.isNotEmpty() && value != "false"
        } else {
            value == literal
        }
    }

    /**
     * Looks up [variable] in [context]:
     *  - `cap.<key>` → "true"/"false" (fatal if `<key>` not in capabilities)
     *  - `target.id` → adapter id
     *  - `family`    → resolved family
     *
     * Any other shape — including `target.<anything-else>` — is treated as
     * an unknown variable, the typo-protection rule the ТЗ calls out.
     */
    private fun resolveVariable(variable: String, context: RenderContext): String {
        if (variable.startsWith("cap.")) {
            val key = variable.removePrefix("cap.")
            val value = context.capabilities[key]
                ?: throw ConditionalRenderException(
                    code = "template_conditional_unknown_variable",
                    path = "",
                    message = "unknown capability `cap.$key` — not declared by target adapter " +
                        "`${context.targetId}` (known: ${context.capabilities.keys.sorted()})",
                )
            return value.toString()
        }
        if (variable == "target.id") return context.targetId
        if (variable == "family") return context.family
        // Anything else under our namespace prefixes — typo guard.
        throw ConditionalRenderException(
            code = "template_conditional_unknown_variable",
            path = "",
            message = "unknown context variable `$variable` " +
                "(supported: `cap.<key>`, `target.id`, `family`)",
        )
    }

    private companion object {
        /** Open tag on its own line (only whitespace around the tag). */
        val OPEN_ANCHORED = Regex("""^\s*\{\{#(if|unless)\s+(.+?)\s*}}\s*$""")

        /** Open tag anywhere in a line — used to detect inline-misuse only. */
        val OPEN_INLINE = Regex("""\{\{#(if|unless)\s+([^}]+?)\s*}}""")

        /** Close tag on its own line. */
        val CLOSE_ANCHORED = Regex("""^\s*\{\{/(if|unless)}}\s*$""")

        /** Strict condition grammar: <var>[ == "literal"]. */
        val CONDITION = Regex(
            """^(cap\.[A-Za-z_][A-Za-z0-9_]*|target\.id|family)""" +
                """(\s*==\s*"([^"]*)")?$""",
        )
    }
}

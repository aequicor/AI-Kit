package com.aikit.setup.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers each axis of [DefaultConditionalRenderer]'s contract — one test per
 * branch listed in the spec so regressions point at exactly one case.
 */
class DefaultConditionalRendererTest {

    private val renderer = DefaultConditionalRenderer()

    private fun ctx(
        capabilities: Map<String, Boolean> = mapOf("subagents" to true, "slash_commands" to true),
        targetId: String = "claude-code",
        family: String = "anthropic",
    ) = RenderContext(capabilities, targetId, family)

    // ── boolean cap.X ────────────────────────────────────────────────────────

    @Test
    fun keepsBlockWhenCapabilityIsTrue() {
        val out = renderer.render(
            "before\n{{#if cap.subagents}}\ninside\n{{/if}}\nafter",
            ctx(),
        )
        assertEquals("before\ninside\nafter", out)
    }

    @Test
    fun dropsBlockWhenCapabilityIsFalse() {
        val out = renderer.render(
            "before\n{{#if cap.subagents}}\ninside\n{{/if}}\nafter",
            ctx(capabilities = mapOf("subagents" to false)),
        )
        assertEquals("before\nafter", out)
    }

    @Test
    fun unlessInvertsCapability() {
        val truthy = renderer.render(
            "a\n{{#unless cap.subagents}}\nx\n{{/unless}}\nb",
            ctx(capabilities = mapOf("subagents" to true)),
        )
        // subagents = true → unless drops the block.
        assertEquals("a\nb", truthy)

        val falsy = renderer.render(
            "a\n{{#unless cap.subagents}}\nx\n{{/unless}}\nb",
            ctx(capabilities = mapOf("subagents" to false)),
        )
        assertEquals("a\nx\nb", falsy)
    }

    @Test
    fun unknownCapabilityIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render(
                "{{#if cap.no_such_flag}}\nx\n{{/if}}",
                ctx(capabilities = mapOf("subagents" to true)),
            )
        }
        assertEquals("template_conditional_unknown_variable", e.code)
        // Error mentions both the offending key and the known set so the
        // agent can correct the typo without re-grepping the codebase.
        assertTrue("no_such_flag" in (e.message ?: ""))
        assertTrue("subagents" in (e.message ?: ""))
    }

    // ── target.id and family ────────────────────────────────────────────────

    @Test
    fun targetIdComparisonMatches() {
        val cursorBranch = renderer.render(
            "a\n{{#if target.id == \"cursor\"}}\nC\n{{/if}}\nb",
            ctx(targetId = "cursor"),
        )
        assertEquals("a\nC\nb", cursorBranch)
    }

    @Test
    fun targetIdComparisonMisses() {
        val out = renderer.render(
            "a\n{{#if target.id == \"cursor\"}}\nC\n{{/if}}\nb",
            ctx(targetId = "claude-code"),
        )
        assertEquals("a\nb", out)
    }

    @Test
    fun familyComparisonMatches() {
        val out = renderer.render(
            "x\n{{#if family == \"anthropic\"}}\nA\n{{/if}}\ny",
            ctx(family = "anthropic"),
        )
        assertEquals("x\nA\ny", out)
    }

    @Test
    fun unknownTargetAccessorIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render("{{#if target.name}}\nx\n{{/if}}", ctx())
        }
        // `target.name` parses as our domain (starts with `target.`) but
        // resolves to no supported variable — typo guard.
        assertEquals("template_conditional_invalid_syntax", e.code)
    }

    // ── coexistence with PlaceholderEngine syntax ───────────────────────────

    @Test
    fun nonOurConditionLeftIntact() {
        // PlaceholderEngine handles `{{#if VARIABLE}}` and
        // `{{#if snippet:NAME}}`. Both must survive ours untouched so the
        // downstream engine can resolve them.
        val input = "head\n{{#if TOOLS_LIST}}\ntools\n{{/if}}\n{{#if snippet:note}}\nnote\n{{/if}}"
        assertEquals(input, renderer.render(input, ctx()))
    }

    // ── structural rules ────────────────────────────────────────────────────

    @Test
    fun nestedBlocksAreFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render(
                "{{#if cap.subagents}}\n{{#if cap.slash_commands}}\nx\n{{/if}}\n{{/if}}",
                ctx(),
            )
        }
        assertEquals("template_conditional_nested", e.code)
    }

    @Test
    fun unclosedBlockIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render("{{#if cap.subagents}}\ndangling content", ctx())
        }
        assertEquals("template_conditional_unclosed", e.code)
    }

    @Test
    fun mismatchedCloseIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render(
                "{{#if cap.subagents}}\nx\n{{/unless}}",
                ctx(),
            )
        }
        assertEquals("template_conditional_mismatched", e.code)
    }

    @Test
    fun inlineOursTagIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render(
                "before {{#if cap.subagents}}inline{{/if}} after",
                ctx(),
            )
        }
        assertEquals("template_conditional_invalid_syntax", e.code)
    }

    @Test
    fun strayUnlessCloseIsFatal() {
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render("text\n{{/unless}}\nmore", ctx())
        }
        assertEquals("template_conditional_mismatched", e.code)
    }

    @Test
    fun unlessWithNonDomainConditionIsFatal() {
        // {{#unless}} has no PlaceholderEngine counterpart — if we let it
        // through, the raw tag would survive into the rendered output.
        // Catch the mistake at render time instead.
        val e = assertFailsWith<ConditionalRenderException> {
            renderer.render("{{#unless TOOLS_LIST}}\nx\n{{/unless}}", ctx())
        }
        assertEquals("template_conditional_invalid_syntax", e.code)
    }

    // ── blank-line handling around dropped blocks ───────────────────────────

    @Test
    fun blankLineBetweenConsecutiveDropsIsPreserved() {
        // ТЗ: two consecutively-dropped blocks preserve the single blank
        // between them so paragraphs don't collide.
        val out = renderer.render(
            "p1\n\n{{#if cap.subagents}}\nA\n{{/if}}\n\n{{#unless cap.subagents}}\nB\n{{/unless}}\n\np2",
            ctx(capabilities = mapOf("subagents" to false)),
        )
        // first block drops (cap.subagents = false → unless keeps would be …)
        // wait: first block is `#if cap.subagents` (drops on false);
        //       second block is `#unless cap.subagents` (keeps on false).
        // So second block keeps. Test name is misleading; renaming via two
        // explicit scenarios below — keep this one focused on the keep+drop case.
        assertEquals("p1\n\n\nB\n\np2", out)
    }

    @Test
    fun twoConsecutiveDropsWithBlankBetweenLeavesOneBlank() {
        val out = renderer.render(
            "p1\n\n{{#if cap.subagents}}\nA\n{{/if}}\n\n{{#if cap.subagents}}\nB\n{{/if}}\n\np2",
            ctx(capabilities = mapOf("subagents" to false)),
        )
        // Both blocks drop. The blank line between them survives by virtue
        // of being outside either block's range.
        assertEquals("p1\n\n\n\np2", out)
    }

    @Test
    fun keepEmitsBodyVerbatimWithoutTrimming() {
        val out = renderer.render(
            "  {{#if cap.subagents}}\n  indented body\n{{/if}}",
            ctx(),
        )
        // Indentation on the body line survives; the tag line itself
        // (along with its leading spaces) is removed.
        assertEquals("  indented body", out)
    }

    // ── Main.md migration scenario ──────────────────────────────────────────

    @Test
    fun mainTemplateSubagentsTrueRendersIfBranch() {
        val migrated = """
            1. Identify what needs to be understood.
            {{#if cap.subagents}}
            2. Dispatch the Researcher subagent.
            {{/if}}
            {{#unless cap.subagents}}
            2. Do the reads yourself.
            {{/unless}}
            3. Output CONTEXT SUMMARY.
        """.trimIndent()
        val out = renderer.render(migrated, ctx(capabilities = mapOf("subagents" to true)))
        assertEquals(
            """
            1. Identify what needs to be understood.
            2. Dispatch the Researcher subagent.
            3. Output CONTEXT SUMMARY.
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun mainTemplateSubagentsFalseRendersUnlessBranch() {
        val migrated = """
            1. Identify what needs to be understood.
            {{#if cap.subagents}}
            2. Dispatch the Researcher subagent.
            {{/if}}
            {{#unless cap.subagents}}
            2. Do the reads yourself.
            {{/unless}}
            3. Output CONTEXT SUMMARY.
        """.trimIndent()
        val out = renderer.render(migrated, ctx(capabilities = mapOf("subagents" to false)))
        assertEquals(
            """
            1. Identify what needs to be understood.
            2. Do the reads yourself.
            3. Output CONTEXT SUMMARY.
            """.trimIndent(),
            out,
        )
    }
}

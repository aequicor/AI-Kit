package com.aikit.setup.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillSectionsTest {

    @Test
    fun parsesStandardLayout() {
        val body = """
            One-line description here.

            # When to invoke

            After X happens.

            # Procedure

            1. Do step one.
            2. Do step two.

            # Output

            A short summary.
        """.trimIndent()
        val s = parseSkillSections(body)
        assertEquals("One-line description here.", s.description)
        assertEquals("After X happens.", s.triggers)
        assertTrue("step one" in s.procedure)
        assertEquals("A short summary.", s.outputFormat)
    }

    @Test
    fun outputFormatAlias() {
        val body = """
            Desc.

            # Output format

            ```
            block here
            ```
        """.trimIndent()
        val s = parseSkillSections(body)
        assertTrue("block here" in s.outputFormat)
    }

    @Test
    fun missingSectionsCollapseToEmpty() {
        val body = """
            Just a description with no markdown sections.
        """.trimIndent()
        val s = parseSkillSections(body)
        assertEquals("Just a description with no markdown sections.", s.description)
        assertEquals("", s.triggers)
        assertEquals("", s.procedure)
        assertEquals("", s.outputFormat)
    }

    @Test
    fun onlyLevelOneHeadingsStartSections() {
        // Sub-headings (`##` and deeper) belong to the current section so
        // authors can structure long procedures with topics without each
        // sub-heading silently becoming its own (unmatched) section. Here
        // the `## Common rules` lives INSIDE the `# Procedure` body.
        val body = """
            Desc.

            # Procedure

            Lead-in.

            ## Common rules

            - rule one
            - rule two

            ## Anti-patterns

            - bad one
        """.trimIndent()
        val s = parseSkillSections(body)
        assertTrue("Common rules" in s.procedure, "got procedure: ${s.procedure}")
        assertTrue("Anti-patterns" in s.procedure, "got procedure: ${s.procedure}")
        assertTrue("rule one" in s.procedure)
    }

    @Test
    fun subHeadingsDoNotMatchTriggerAliases() {
        // Even if a sub-heading happens to be `## When to invoke` (matching
        // a triggers alias), it does NOT start a new section — only `#` does.
        val body = """
            Desc.

            # Procedure

            See sub-heading below.

            ## When to invoke

            Not actually triggers.
        """.trimIndent()
        val s = parseSkillSections(body)
        assertEquals("", s.triggers)
        assertTrue("Not actually triggers." in s.procedure)
    }

    @Test
    fun headingsInsideFencedCodeBlocksAreNotSectionBreakers() {
        // Regression: aikit-plan-artifact's procedure carries a markdown
        // TEMPLATE wrapped in ```markdown ... ```; that template begins with
        // `# <Task title>`. Before the fence-aware fix the sectioniser saw
        // that line as a real H1, closed `# Procedure` after four lines, and
        // dropped the rest (the actual template, verb vocabulary, id
        // convention) into a phantom `<task title>` section that matched no
        // alias and got discarded.
        val body = """
            Desc.

            # Procedure

            ## File template

            ```markdown
            # <Task title>

            **Created:** <YYYY-MM-DD>

            ## Context

            stuff
            ```

            ## Verify verbs

            After the fence, still inside procedure.

            # Output format

            result here
        """.trimIndent()
        val s = parseSkillSections(body)
        assertTrue("# <Task title>" in s.procedure, "fenced H1 dropped: ${s.procedure}")
        assertTrue("Verify verbs" in s.procedure, "post-fence content dropped: ${s.procedure}")
        assertTrue("After the fence" in s.procedure)
        assertEquals("result here", s.outputFormat)
    }

    @Test
    fun tildeFencesAlsoSuppressHeadingDetection() {
        // Tilde fences (`~~~`) are CommonMark-equivalent to backtick fences;
        // a `# Heading` inside a tilde block must not start a new section.
        val body = """
            Desc.

            # Procedure

            ~~~
            # Not a heading
            ~~~

            Tail.

            # Output

            done
        """.trimIndent()
        val s = parseSkillSections(body)
        assertTrue("Not a heading" in s.procedure, "tilde-fenced H1 dropped: ${s.procedure}")
        assertTrue("Tail." in s.procedure)
        assertEquals("done", s.outputFormat)
    }

    @Test
    fun htmlCommentBeforeDescriptionIsSkipped() {
        // The optional-skill marker is an HTML comment on line 1. The
        // description detector must ignore comment-only lines so the marker
        // does not leak into the rendered SKILL_DESCRIPTION variable.
        val body = """
            <!-- aikit:optional -->
            Real description follows.

            # Procedure

            Do thing.
        """.trimIndent()
        val s = parseSkillSections(body)
        assertEquals("Real description follows.", s.description)
    }

    @Test
    fun detectsOptionalSkillMarker() {
        val optionalBody = """
            <!-- aikit:optional -->
            Optional skill description.
        """.trimIndent()
        val coreBody = """
            Core skill description.

            # Procedure
            Do thing.
        """.trimIndent()
        assertTrue(isOptionalSkill(optionalBody))
        assertFalse(isOptionalSkill(coreBody))
    }

    @Test
    fun optionalMarkerToleratesSurroundingWhitespace() {
        // The marker may appear with extra whitespace inside the comment
        // (`<!--  aikit:optional  -->`) but a different payload must not opt in.
        assertTrue(isOptionalSkill("<!--   aikit:optional   -->"))
        assertTrue(isOptionalSkill("body text\n<!-- aikit:optional -->\nmore"))
        assertFalse(isOptionalSkill("<!-- aikit:other -->"))
        assertFalse(isOptionalSkill("<!-- aikit -->"))
    }
}

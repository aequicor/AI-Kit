package com.aikit.setup.generation

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun headingDetectedAtAnyLevel() {
        val body = """
            Desc.

            ### When to invoke

            On Thursdays.
        """.trimIndent()
        val s = parseSkillSections(body)
        assertEquals("On Thursdays.", s.triggers)
    }
}

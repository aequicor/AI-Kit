package com.aikit.setup.generation

import com.aikit.setup.templates.InMemoryTemplateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderEngineTest {

    @Test
    fun substitutesSimpleVariables() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "Hello, {{NAME}}! Stack: {{STACK}}.",
            mapOf("NAME" to "Demo", "STACK" to "kotlin"),
        )
        assertEquals("Hello, Demo! Stack: kotlin.", out)
    }

    @Test
    fun unknownVariablesAreLeftIntact() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render("untouched: {{MISSING}}", emptyMap())
        // Surviving placeholder is intentional — surfaces typos in template authoring.
        assertTrue("{{MISSING}}" in out)
    }

    @Test
    fun expandsDialectLocalSnippet() {
        val templates = InMemoryTemplateRegistry(
            mapOf("dialects/x/snippets/note.md" to "(dialect note: {{NAME}})"),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", "_shared")
        val out = engine.render("before {{snippet:note}} after", mapOf("NAME" to "n"))
        assertEquals("before (dialect note: n) after", out)
    }

    @Test
    fun fallsBackFromDialectToSharedSnippet() {
        val templates = InMemoryTemplateRegistry(
            mapOf("_shared/snippets/note.md" to "(shared note)"),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", "_shared")
        val out = engine.render("[{{snippet:note}}]", emptyMap())
        assertEquals("[(shared note)]", out)
    }

    @Test
    fun missingSnippetCollapsesToEmpty() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", "_shared")
        val out = engine.render("[{{snippet:absent}}]", emptyMap())
        // No fallback → empty inlined; `{{snippet:…}}` itself disappears.
        assertEquals("[]", out)
    }

    @Test
    fun nestedSnippetExpansion() {
        val templates = InMemoryTemplateRegistry(
            mapOf(
                "dialects/x/snippets/outer.md" to "OUTER[{{snippet:inner}}]",
                "dialects/x/snippets/inner.md" to "INNER",
            ),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", null)
        assertEquals("OUTER[INNER]", engine.render("{{snippet:outer}}", emptyMap()))
    }

    @Test
    fun cyclicSnippetsDoNotInfiniteLoop() {
        val templates = InMemoryTemplateRegistry(
            mapOf(
                "dialects/x/snippets/a.md" to "A[{{snippet:b}}]",
                "dialects/x/snippets/b.md" to "B[{{snippet:a}}]",
            ),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", null)
        // The depth cap halts expansion; we assert the engine returns
        // (rather than hanging) and the result mentions both labels.
        val out = engine.render("{{snippet:a}}", emptyMap())
        assertTrue("A" in out && "B" in out)
    }
}

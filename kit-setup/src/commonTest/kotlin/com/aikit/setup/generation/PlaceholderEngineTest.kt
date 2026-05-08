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
    fun ifVariableTruthyKeepsBlock() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "before\n{{#if TOOLS}}\n<tools>\n{{TOOLS}}\n</tools>\n{{/if}}\nafter",
            mapOf("TOOLS" to "read,write"),
        )
        assertEquals("before\n<tools>\nread,write\n</tools>\nafter", out)
    }

    @Test
    fun ifVariableMissingDropsBlock() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "before\n{{#if TOOLS}}\n<tools>\n{{TOOLS}}\n</tools>\n{{/if}}\nafter",
            emptyMap(),
        )
        assertEquals("before\nafter", out)
    }

    @Test
    fun ifVariableBlankDropsBlock() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "A\n{{#if X}}\nkept\n{{/if}}\nB",
            mapOf("X" to "   "),
        )
        assertEquals("A\nB", out)
    }

    @Test
    fun ifSnippetPresentAndNonBlankKeepsBlock() {
        val templates = InMemoryTemplateRegistry(
            mapOf("dialects/x/snippets/note.md" to "(note for {{NAME}})"),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", null)
        val out = engine.render(
            "head\n{{#if snippet:note}}\n## Note\n{{snippet:note}}\n{{/if}}\ntail",
            mapOf("NAME" to "demo"),
        )
        assertEquals("head\n## Note\n(note for demo)\ntail", out)
    }

    @Test
    fun ifSnippetMissingDropsBlock() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "head\n{{#if snippet:absent}}\n## Section\n{{snippet:absent}}\n{{/if}}\ntail",
            emptyMap(),
        )
        assertEquals("head\ntail", out)
    }

    @Test
    fun ifSnippetBlankAfterSubstitutionDropsBlock() {
        // Snippet exists but expands to nothing once {{NAME}} resolves to "".
        val templates = InMemoryTemplateRegistry(
            mapOf("dialects/x/snippets/note.md" to "{{NAME}}"),
        )
        val engine = PlaceholderEngine(templates, "dialects/x", null)
        val out = engine.render(
            "head\n{{#if snippet:note}}\nkept\n{{/if}}\ntail",
            mapOf("NAME" to ""),
        )
        assertEquals("head\ntail", out)
    }

    @Test
    fun nestedIfBlocksResolveInnermostFirst() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render(
            "before\n{{#if A}}\nouter\n{{#if B}}\ninner\n{{/if}}\nend\n{{/if}}\nafter",
            mapOf("A" to "1", "B" to ""),
        )
        // B is falsy → inner block drops; A is truthy → outer kept.
        assertEquals("before\nouter\nend\nafter", out)
    }

    @Test
    fun unmatchedIfLeftIntact() {
        val engine = PlaceholderEngine(InMemoryTemplateRegistry(emptyMap()), "dialects/x", null)
        val out = engine.render("before {{#if X}} dangling", mapOf("X" to "v"))
        // No closing tag → engine bails out, surfacing the bug instead of guessing.
        assertTrue("{{#if X}}" in out)
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

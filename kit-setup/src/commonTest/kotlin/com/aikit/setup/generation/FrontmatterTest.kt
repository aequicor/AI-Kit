package com.aikit.setup.generation

import kotlin.test.Test
import kotlin.test.assertEquals

class FrontmatterTest {

    @Test
    fun substitutesPlaceholdersAndWrapsInDelimiters() {
        val rendered = Frontmatter.render(
            template = """name: "{{NAME}}"
description: "{{DESC}}"""",
            variables = mapOf("NAME" to "Main", "DESC" to "v3 driver"),
        )
        assertEquals(
            "---\n" +
                "name: \"Main\"\n" +
                "description: \"v3 driver\"\n" +
                "---\n",
            rendered,
        )
    }

    @Test
    fun stripsLinesWhereValueResolvedToEmptyString() {
        // Real-world case: `allowed-tools: "{{TOOLS_LIST_CSV}}"` becomes
        // `allowed-tools: ""` when a command declares no tools. Claude Code
        // reads `""` as "no tools allowed" and the slash command becomes a
        // no-op. Dropping the line yields a frontmatter where Claude Code
        // falls back to the default tool set.
        val rendered = Frontmatter.render(
            template = """description: "{{DESC}}"
allowed-tools: "{{TOOLS_LIST_CSV}}"""",
            variables = mapOf("DESC" to "Run kit", "TOOLS_LIST_CSV" to ""),
        )
        assertEquals(
            "---\n" +
                "description: \"Run kit\"\n" +
                "---\n",
            rendered,
        )
    }

    @Test
    fun stripsBothSingleAndDoubleEmptyQuotes() {
        val rendered = Frontmatter.render(
            template = "name: \"{{N}}\"\nfoo: ''\nbar: \"\"\nkeep: \"x\"",
            variables = mapOf("N" to "agent"),
        )
        assertEquals(
            "---\n" +
                "name: \"agent\"\n" +
                "keep: \"x\"\n" +
                "---\n",
            rendered,
        )
    }

    @Test
    fun keepsLineWithExplicitNonEmptyValue() {
        // `tools: "*"` and `tools: "a, b"` must survive — only the literal
        // empty-string pattern is dropped.
        val rendered = Frontmatter.render(
            template = "tools: \"{{T}}\"",
            variables = mapOf("T" to "Read, Edit"),
        )
        assertEquals(
            "---\n" +
                "tools: \"Read, Edit\"\n" +
                "---\n",
            rendered,
        )
    }

    @Test
    fun doesNotStripListOrMappingValues() {
        // A defensive case: even if a list comes in empty, we keep the line —
        // the regex matches only scalar empty strings.
        val rendered = Frontmatter.render(
            template = "tags: []\nmodel: \"\"\nnested:\n  key: value",
            variables = emptyMap(),
        )
        assertEquals(
            "---\n" +
                "tags: []\n" +
                "nested:\n" +
                "  key: value\n" +
                "---\n",
            rendered,
        )
    }
}

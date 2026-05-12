package com.aikit.setup.manifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockYamlParserTest {

    private val parser = BlockYamlParser()

    @Test
    fun emptyDocumentReturnsNull() {
        assertIs<RawNode.Null>(parser.parse(""))
        assertIs<RawNode.Null>(parser.parse("   \n  \n"))
        assertIs<RawNode.Null>(parser.parse("# only a comment\n"))
    }

    @Test
    fun blockMappingFlatScalars() {
        val root = parser.parse(
            """
            name: kit-setup
            version: 1.0.0
            enabled: true
            """.trimIndent(),
        )
        val map = assertIs<RawNode.Mapping>(root)
        assertEquals("kit-setup", map.entries["name"].stringOrNull())
        assertEquals("1.0.0", map.entries["version"].stringOrNull())
        assertEquals(true, map.entries["enabled"].boolOrNull())
    }

    @Test
    fun blockMappingPreservesInsertionOrder() {
        val root = parser.parse(
            """
            zeta: 1
            alpha: 2
            mu: 3
            """.trimIndent(),
        )
        val map = assertIs<RawNode.Mapping>(root)
        assertEquals(listOf("zeta", "alpha", "mu"), map.entries.keys.toList())
    }

    @Test
    fun nestedBlockMapping() {
        val root = parser.parse(
            """
            project:
              name: Demo
              slug: demo
            """.trimIndent(),
        )
        assertEquals("Demo", root.path("project", "name").stringOrNull())
        assertEquals("demo", root.path("project", "slug").stringOrNull())
    }

    @Test
    fun blockSequenceOfScalars() {
        val root = parser.parse(
            """
            languages:
              - typescript
              - kotlin
            """.trimIndent(),
        )
        assertEquals(listOf("typescript", "kotlin"), root.field("languages").asStringList())
    }

    @Test
    fun blockSequenceOfMappings() {
        val root = parser.parse(
            """
            models:
              - id: opus
                tier: reasoner
              - id: haiku
                tier: fast
            """.trimIndent(),
        )
        val items = root.field("models").asList()
        assertEquals(2, items.size)
        assertEquals("opus", items[0].field("id").stringOrNull())
        assertEquals("reasoner", items[0].field("tier").stringOrNull())
        assertEquals("haiku", items[1].field("id").stringOrNull())
    }

    @Test
    fun flowSequenceParses() {
        val root = parser.parse("languages: [typescript, kotlin, python]")
        assertEquals(listOf("typescript", "kotlin", "python"), root.field("languages").asStringList())
    }

    @Test
    fun flowMappingParses() {
        val root = parser.parse("params: { temperature: 0.2, max_tokens: 8000 }")
        assertEquals("0.2", root.path("params", "temperature").stringOrNull())
        assertEquals("8000", root.path("params", "max_tokens").stringOrNull())
    }

    @Test
    fun nestedFlowMappingInSequence() {
        val root = parser.parse(
            """
            steps:
              - { agent: A, task: planning, gate: approve }
              - { agent: B, task: implementation, gate: auto }
            """.trimIndent(),
        )
        val items = root.field("steps").asList()
        assertEquals("A", items[0].field("agent").stringOrNull())
        assertEquals("planning", items[0].field("task").stringOrNull())
        assertEquals("auto", items[1].field("gate").stringOrNull())
    }

    @Test
    fun quotedScalarsPreserveSpacing() {
        val root = parser.parse(
            """
            description: "Reviews changes for OWASP top-10 issues."
            single: 'verbatim # not a comment'
            """.trimIndent(),
        )
        assertEquals("Reviews changes for OWASP top-10 issues.", root.field("description").stringOrNull())
        assertEquals("verbatim # not a comment", root.field("single").stringOrNull())
    }

    @Test
    fun commentsStrippedAtTopLevelButNotInsideQuotes() {
        val root = parser.parse(
            """
            kept: value     # tail comment
            note: "x # inside quotes"
            """.trimIndent(),
        )
        assertEquals("value", root.field("kept").stringOrNull())
        assertEquals("x # inside quotes", root.field("note").stringOrNull())
    }

    @Test
    fun nullScalarsRecognized() {
        val root = parser.parse(
            """
            a: null
            b: ~
            c:
            """.trimIndent(),
        )
        assertIs<RawNode.Null>(root.field("a")!!)
        assertIs<RawNode.Null>(root.field("b")!!)
        assertIs<RawNode.Null>(root.field("c")!!)
    }

    @Test
    fun documentSeparatorsTolerated() {
        val root = parser.parse(
            """
            ---
            id: kit
            ...
            """.trimIndent(),
        )
        assertEquals("kit", root.field("id").stringOrNull())
    }

    @Test
    fun colonInQuotedValueIsNotASeparator() {
        val root = parser.parse("""url: "https://example.com:8080/path"""")
        assertEquals("https://example.com:8080/path", root.field("url").stringOrNull())
    }

    @Test
    fun missingColonOnNonSequenceLineIsAnError() {
        assertFailsWith<YamlParseException> {
            parser.parse(
                """
                project:
                  garbageline
                """.trimIndent(),
            )
        }
    }

    @Test
    fun leadingUtf8BomIsStripped() {
        // Some Windows tools (older PowerShell `Set-Content -Encoding utf8`)
        // prefix YAML files with U+FEFF. Without stripping, the first key
        // would carry an invisible BOM and validation would lie about which
        // keys are present.
        val bom = Char(0xFEFF).toString()
        val root = parser.parse(bom + "manifest_version: \"1.0.0\"\nproject:\n  slug: demo")
        assertEquals("1.0.0", root.field("manifest_version").stringOrNull())
        assertEquals("demo", root.path("project", "slug").stringOrNull())
        // The BOM must not bleed into the first key name.
        val keys = (root as RawNode.Mapping).entries.keys
        assertTrue(keys.contains("manifest_version"), "BOM should not corrupt the first key; got keys=$keys")
    }

    @Test
    fun absentBomLeavesContentIntact() {
        val root = parser.parse("manifest_version: \"1.0.0\"")
        assertEquals("1.0.0", root.field("manifest_version").stringOrNull())
    }

    @Test
    fun foldedBlockScalarRejectedWithHint() {
        val ex = assertFailsWith<YamlParseException> {
            parser.parse(
                """
                description: >
                  multi
                  line
                """.trimIndent(),
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Folded/literal block scalars"), "expected block-scalar hint, got: $msg")
        assertTrue(msg.contains(">"), "expected indicator in message, got: $msg")
    }

    @Test
    fun literalBlockScalarRejectedWithHint() {
        val ex = assertFailsWith<YamlParseException> {
            parser.parse(
                """
                note: |
                  line 1
                  line 2
                """.trimIndent(),
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Folded/literal block scalars"), "expected block-scalar hint, got: $msg")
    }

    @Test
    fun yamlAnchorRejectedWithHint() {
        val ex = assertFailsWith<YamlParseException> {
            parser.parse("ref: &shared 42")
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("anchor"), "expected anchor hint, got: $msg")
        assertTrue(msg.contains("Quote the value"), "expected quoting hint, got: $msg")
    }

    @Test
    fun yamlAliasRejectedWithHint() {
        val ex = assertFailsWith<YamlParseException> {
            parser.parse("copy: *shared")
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("alias"), "expected alias hint, got: $msg")
    }

    @Test
    fun anchorInSequenceItemRejected() {
        val ex = assertFailsWith<YamlParseException> {
            parser.parse(
                """
                items:
                  - &first one
                  - two
                """.trimIndent(),
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("anchor"), "expected anchor hint, got: $msg")
    }

    @Test
    fun quotedAmpersandIsAccepted() {
        // Literal `&` at the start of a scalar is allowed inside quotes —
        // the anchor check only fires on unquoted plain scalars.
        val root = parser.parse("pattern: \"&literal\"")
        assertEquals("&literal", root.field("pattern").stringOrNull())
    }

    @Test
    fun quotedStarIsAccepted() {
        val root = parser.parse("glob: \"*.kt\"")
        assertEquals("*.kt", root.field("glob").stringOrNull())
    }

    @Test
    fun blockScalarChompingVariantsRejected() {
        for (indicator in listOf(">-", "|-", ">+", "|+")) {
            val ex = assertFailsWith<YamlParseException>("indicator $indicator should be rejected") {
                parser.parse("key: $indicator\n  child")
            }
            val msg = ex.message ?: ""
            assertTrue(
                msg.contains("Folded/literal block scalars"),
                "expected block-scalar hint for $indicator, got: $msg",
            )
        }
    }

    @Test
    fun realManifestSnippetParsesEndToEnd() {
        val src = """
            manifest_version: "1.0.0"
            project:
              name: My Project
              slug: my-project
            targets:
              - id: claude-code
                native_provider: anthropic
                can_use_via: []
            agents:
              - id: CodeWriter
                model_selection:
                  needs: [code]
                  prefers: balanced
                  by_task:
                    debug: { prefers: reasoner }
                prompt:
                  default: { include: prompts/CodeWriter.md }
        """.trimIndent()
        val root = parser.parse(src)
        assertEquals("1.0.0", root.field("manifest_version").stringOrNull())
        assertEquals("CodeWriter", root.path("agents").asList()[0].field("id").stringOrNull())
        assertEquals(
            "reasoner",
            root.path("agents").asList()[0]
                .path("model_selection", "by_task", "debug", "prefers").stringOrNull(),
        )
        assertTrue(root.field("targets").asList().isNotEmpty())
    }
}

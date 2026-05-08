package com.aikit.setup.manifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepMergeTest {

    private val parser = BlockYamlParser()

    private fun parse(yaml: String): RawNode = parser.parse(yaml.trimIndent())

    @Test
    fun overlayScalarOverridesBaseScalar() {
        val merged = DeepMerge.merge(parse("a: 1"), parse("a: 2"))
        assertEquals("2", merged.path("a").stringOrNull())
    }

    @Test
    fun overlayKeepsUntouchedBaseFields() {
        val base = parse(
            """
            a: 1
            b: 2
            """,
        )
        val overlay = parse("a: 9")
        val merged = DeepMerge.merge(base, overlay)
        assertEquals("9", merged.path("a").stringOrNull())
        // b still present and untouched.
        assertEquals("2", merged.path("b").stringOrNull())
    }

    @Test
    fun mappingsMergePerKey() {
        val base = parse(
            """
            stack:
              languages: [kotlin]
              build_command: "./gradlew"
            """,
        )
        val overlay = parse(
            """
            stack:
              build_command: "make"
              run_command: "make run"
            """,
        )
        val merged = DeepMerge.merge(base, overlay)
        assertEquals("make", merged.path("stack", "build_command").stringOrNull())
        assertEquals("make run", merged.path("stack", "run_command").stringOrNull())
        // languages kept from base.
        assertEquals(listOf("kotlin"), merged.path("stack", "languages").asStringList())
    }

    @Test
    fun stringListsConcatAndDedupe() {
        val base = parse(
            """
            policies:
              forbidden_patterns:
                - "no console.log"
                - "no any"
            """,
        )
        val overlay = parse(
            """
            policies:
              forbidden_patterns:
                - "no any"
                - "no @ts-ignore"
            """,
        )
        val merged = DeepMerge.merge(base, overlay)
        assertEquals(
            listOf("no console.log", "no any", "no @ts-ignore"),
            merged.path("policies", "forbidden_patterns").asStringList(),
        )
    }

    @Test
    fun idKeyedListsMergeById() {
        val base = parse(
            """
            tools:
              - id: serena
                kind: mcp-stdio
                command: serena-mcp
              - id: lsp-kotlin
                kind: lsp
                command: kotlin-lsp
            """,
        )
        val overlay = parse(
            """
            tools:
              - id: lsp-kotlin
                command: kotlin-lsp-v2
              - id: web-search
                kind: builtin
            """,
        )
        val merged = DeepMerge.merge(base, overlay)
        val tools = merged.path("tools")
        assertTrue(tools is RawNode.Sequence)
        val byId = tools.items.associate {
            (it as RawNode.Mapping).entries["id"]!!.stringOrNull()!! to it
        }
        // serena untouched.
        assertEquals("serena-mcp", byId["serena"]?.field("command")?.stringOrNull())
        // lsp-kotlin command overridden, kind preserved from base.
        assertEquals("kotlin-lsp-v2", byId["lsp-kotlin"]?.field("command")?.stringOrNull())
        assertEquals("lsp", byId["lsp-kotlin"]?.field("kind")?.stringOrNull())
        // web-search appended.
        assertEquals("builtin", byId["web-search"]?.field("kind")?.stringOrNull())
        // Order: base first, then overlay-only.
        assertEquals(listOf("serena", "lsp-kotlin", "web-search"), byId.keys.toList())
    }

    @Test
    fun mismatchedTypesLetOverlayWin() {
        // base has scalar, overlay has list — overlay wins outright.
        val merged = DeepMerge.merge(parse("a: 1"), parse("a: [1, 2]"))
        assertEquals(listOf("1", "2"), merged.path("a").asStringList())
    }

    @Test
    fun emptyOverlayMappingPreservesBase() {
        val base = parse(
            """
            a: 1
            b: 2
            """,
        )
        val overlay: RawNode = RawNode.Mapping(emptyMap())
        val merged = DeepMerge.merge(base, overlay)
        assertEquals("1", merged.path("a").stringOrNull())
        assertEquals("2", merged.path("b").stringOrNull())
    }

    @Test
    fun overlayAddsNewMappingKeysWithDeclaredOrder() {
        val base = parse("first: 1")
        val overlay = parse(
            """
            second: 2
            third: 3
            """,
        )
        val merged = DeepMerge.merge(base, overlay) as RawNode.Mapping
        assertEquals(listOf("first", "second", "third"), merged.entries.keys.toList())
    }

    @Test
    fun listsWithoutIdsConcatEvenWhenMappingsPresent() {
        // Items without an `id` field are not id-keyed; should concat+dedupe.
        val base = parse(
            """
            xs:
              - { name: a }
              - { name: b }
            """,
        )
        val overlay = parse(
            """
            xs:
              - { name: b }
              - { name: c }
            """,
        )
        val merged = DeepMerge.merge(base, overlay)
        val items = merged.path("xs").asList()
        assertEquals(3, items.size)
        assertEquals(
            listOf("a", "b", "c"),
            items.map { it.field("name").stringOrNull() },
        )
    }
}

package com.aikit.setup.output

import com.aikit.setup.command.ProfileAxisInfo
import com.aikit.setup.command.ProfileEntry
import com.aikit.setup.command.SchemaCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaResultRendererTest {

    private val catalog = SchemaCatalog(
        kitVersion = "9.9.9",
        manifestSchemaVersion = "1.0.0",
        agents = listOf("Architect", "CodeWriter"),
        agentDialectVariants = mapOf("CodeWriter" to listOf("anthropic", "qwen")),
        commands = listOf("kit-fix"),
        skills = listOf("bug-retro"),
        promptDialects = listOf("anthropic", "openai"),
        targetAdapters = listOf("claude-code"),
        knowledgeSections = listOf("conventions"),
        sharedSnippets = listOf("project_context"),
        rules = listOf("typescript/typescript-strict"),
        userPrompts = listOf("explore-module"),
        profileAxes = listOf(
            ProfileAxisInfo("language", "exactly_one"),
            ProfileAxisInfo("framework", "zero_or_more"),
            ProfileAxisInfo("capability", "zero_or_more"),
        ),
        profiles = listOf(
            ProfileEntry("typescript-pnpm", "language", "TS + pnpm"),
            ProfileEntry("nextjs", "framework", "Next.js"),
            ProfileEntry("solid", "capability", "SOLID rules"),
        ),
        enums = linkedMapOf(
            "provider_auth" to listOf("api_key", "subscription", "none"),
            "model_tier" to listOf("fast", "balanced", "reasoner"),
            "cost_hint" to listOf("cheap", "balanced", "premium"),
            "knowledge_store_kind" to listOf("filesystem", "mcp", "http", "composite"),
        ),
    )

    @Test
    fun jsonRendererProducesStableSingleLine() {
        val rendered = JsonSchemaResultRenderer().render(catalog)

        // Single line, fixed key order — agents pattern-match on this shape.
        assertEquals(
            """{"kit_version":"9.9.9",""" +
                """"manifest_schema_version":"1.0.0",""" +
                """"agents":["Architect","CodeWriter"],""" +
                """"agent_dialect_variants":{"CodeWriter":["anthropic","qwen"]},""" +
                """"commands":["kit-fix"],""" +
                """"skills":["bug-retro"],""" +
                """"prompt_dialects":["anthropic","openai"],""" +
                """"target_adapters":["claude-code"],""" +
                """"knowledge_sections":["conventions"],""" +
                """"shared_snippets":["project_context"],""" +
                """"rules":["typescript/typescript-strict"],""" +
                """"user_prompts":["explore-module"],""" +
                """"profile_axes":[""" +
                """{"name":"language","cardinality":"exactly_one"},""" +
                """{"name":"framework","cardinality":"zero_or_more"},""" +
                """{"name":"capability","cardinality":"zero_or_more"}],""" +
                """"profiles":[""" +
                """{"name":"typescript-pnpm","axis":"language","description":"TS + pnpm"},""" +
                """{"name":"nextjs","axis":"framework","description":"Next.js"},""" +
                """{"name":"solid","axis":"capability","description":"SOLID rules"}],""" +
                """"enums":{""" +
                """"provider_auth":["api_key","subscription","none"],""" +
                """"model_tier":["fast","balanced","reasoner"],""" +
                """"cost_hint":["cheap","balanced","premium"],""" +
                """"knowledge_store_kind":["filesystem","mcp","http","composite"]}}""",
            rendered,
        )
    }

    @Test
    fun jsonRendererEmitsNullDescriptionForProfileWithoutOne() {
        val withMissingDesc = catalog.copy(
            profiles = listOf(ProfileEntry("bare", "language", null)),
        )
        val rendered = JsonSchemaResultRenderer().render(withMissingDesc)
        assertTrue(
            rendered.contains(""""profiles":[{"name":"bare","axis":"language","description":null}]"""),
            "got: $rendered",
        )
    }

    @Test
    fun humanRendererStarsAgentsWithVariants() {
        val rendered = HumanSchemaResultRenderer().render(catalog)

        assertTrue(rendered.contains("kit_version 9.9.9"))
        assertTrue(rendered.contains("CodeWriter*"))
        assertTrue(rendered.contains("CodeWriter has dialect variants: anthropic, qwen"))
        assertTrue(rendered.contains("prompt_dialects"))
        assertTrue(rendered.contains("target_adapters"))
    }

    @Test
    fun humanRendererGroupsProfilesByAxisWithCardinality() {
        val rendered = HumanSchemaResultRenderer().render(catalog)

        // Header line shows total count across all axes.
        assertTrue(rendered.contains("profiles") && rendered.contains("(3) :"), "got: $rendered")
        // Each axis line has a humanised cardinality + per-axis count.
        assertTrue(rendered.contains("language (exactly 1, 1):"), "got: $rendered")
        assertTrue(rendered.contains("framework (0..N, 1):"), "got: $rendered")
        assertTrue(rendered.contains("capability (0..N, 1):"), "got: $rendered")
        // Profile rows carry their description after an em-dash.
        assertTrue(rendered.contains("* typescript-pnpm — TS + pnpm"), "got: $rendered")
        assertTrue(rendered.contains("* nextjs — Next.js"), "got: $rendered")
        assertTrue(rendered.contains("* solid — SOLID rules"), "got: $rendered")
    }

    @Test
    fun humanRendererMarksAxesWithoutBundledProfiles() {
        val emptyAxes = catalog.copy(
            profiles = listOf(ProfileEntry("typescript-pnpm", "language", "TS")),
        )
        val rendered = HumanSchemaResultRenderer().render(emptyAxes)

        assertTrue(rendered.contains("language (exactly 1, 1):"), "got: $rendered")
        assertTrue(rendered.contains("framework (0..N, 0):"), "got: $rendered")
        assertTrue(rendered.contains("capability (0..N, 0):"), "got: $rendered")
        // Empty buckets get an explicit placeholder so users can see the axis exists.
        assertTrue(rendered.contains("(none bundled)"), "got: $rendered")
    }

    @Test
    fun humanRendererListsEnumValuesPerField() {
        val rendered = HumanSchemaResultRenderer().render(catalog)

        assertTrue(rendered.contains("enums") && rendered.contains("(4) :"), "got: $rendered")
        assertTrue(
            rendered.contains("provider_auth : api_key, subscription, none"),
            "got: $rendered",
        )
        assertTrue(rendered.contains("model_tier : fast, balanced, reasoner"), "got: $rendered")
        assertTrue(rendered.contains("cost_hint : cheap, balanced, premium"), "got: $rendered")
        assertTrue(
            rendered.contains("knowledge_store_kind : filesystem, mcp, http, composite"),
            "got: $rendered",
        )
    }
}

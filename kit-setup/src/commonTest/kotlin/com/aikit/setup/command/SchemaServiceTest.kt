package com.aikit.setup.command

import com.aikit.setup.templates.InMemoryTemplateRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaServiceTest {

    @Test
    fun groupsTemplateKeysByCategory() {
        val registry = InMemoryTemplateRegistry(
            mapOf(
                "prompts/Architect.md" to "",
                "prompts/CodeWriter.md" to "",
                "prompts/CodeWriter.anthropic.md" to "",
                "prompts/CodeWriter.qwen.md" to "",
                "prompts/Verifier.md" to "",
                "commands/kit-approve.md" to "",
                "commands/kit-fix.md" to "",
                "skills/bug-retro/SKILL.md" to "",
                "skills/bug-retro/output.md" to "",
                "skills/look-up/SKILL.md" to "",
                "dialects/anthropic/dialect.yaml" to "",
                "dialects/anthropic/wrappers/agent.md" to "",
                "dialects/openai/dialect.yaml" to "",
                "target_adapters/claude-code/adapter.yaml" to "",
                "target_adapters/cursor/adapter.yaml" to "",
                "knowledge/conventions.md" to "",
                "knowledge/routing-table.md" to "",
                "_shared/snippets/project_context.md" to "",
                "_shared/snippets/stack_summary.md" to "",
                "rules/typescript/typescript-strict.md" to "",
                "user-prompts/explore-module.md" to "",
                "schema/kit-manifect.schema.json" to "",
            ),
        )

        val catalog = SchemaService(registry, kitVersion = "9.9.9").catalog()

        assertEquals("9.9.9", catalog.kitVersion)
        assertEquals("1.0.0", catalog.manifestSchemaVersion)
        assertEquals(listOf("Architect", "CodeWriter", "Verifier"), catalog.agents)
        assertEquals(
            mapOf("CodeWriter" to listOf("anthropic", "qwen")),
            catalog.agentDialectVariants,
        )
        assertEquals(listOf("kit-approve", "kit-fix"), catalog.commands)
        assertEquals(listOf("bug-retro", "look-up"), catalog.skills)
        assertEquals(listOf("anthropic", "openai"), catalog.promptDialects)
        assertEquals(listOf("claude-code", "cursor"), catalog.targetAdapters)
        assertEquals(listOf("conventions", "routing-table"), catalog.knowledgeSections)
        assertEquals(listOf("project_context", "stack_summary"), catalog.sharedSnippets)
        assertEquals(listOf("typescript/typescript-strict"), catalog.rules)
        assertEquals(listOf("explore-module"), catalog.userPrompts)

        // Profile axes always emitted with stable cardinality contract, even
        // when no profiles are bundled in this test fixture.
        assertEquals(
            listOf(
                ProfileAxisInfo("language", "exactly_one"),
                ProfileAxisInfo("framework", "zero_or_more"),
                ProfileAxisInfo("capability", "zero_or_more"),
            ),
            catalog.profileAxes,
        )
        assertTrue(catalog.profiles.isEmpty())

        // Enum catalog is hand-written and independent of the bundle — agents
        // need it to author manifests with the canonical values.
        assertEquals(
            listOf("provider_auth", "model_tier", "cost_hint", "knowledge_store_kind", "agent_role"),
            catalog.enums.keys.toList(),
        )
        assertEquals(
            listOf("api_key", "subscription", "none"),
            catalog.enums.getValue("provider_auth"),
        )
        assertEquals(
            listOf("fast", "balanced", "reasoner"),
            catalog.enums.getValue("model_tier"),
        )
        assertEquals(
            listOf("cheap", "balanced", "premium"),
            catalog.enums.getValue("cost_hint"),
        )
        assertEquals(
            listOf("filesystem", "mcp", "http", "composite"),
            catalog.enums.getValue("knowledge_store_kind"),
        )
        assertEquals(
            listOf("orchestrator", "subagent"),
            catalog.enums.getValue("agent_role"),
        )
    }

    @Test
    fun enumeratesProfilesByAxisAndExtractsDescription() {
        val registry = InMemoryTemplateRegistry(
            mapOf(
                "profiles/README.md" to "ignored",
                "profiles/profile.schema.json" to "ignored",
                "profiles/language/typescript-pnpm.yaml" to """
                    _profile_name: typescript-pnpm
                    _profile_description: "TypeScript + pnpm baseline"
                    _profile_axis: language
                """.trimIndent(),
                "profiles/language/kotlin-gradle.yaml" to """
                    _profile_name: kotlin-gradle
                    _profile_description: "Kotlin + Gradle (KTS)"
                    _profile_axis: language
                """.trimIndent(),
                "profiles/framework/nextjs.yaml" to """
                    _profile_name: nextjs
                    _profile_description: "Next.js"
                    _profile_axis: framework
                """.trimIndent(),
                "profiles/capability/solid.yaml" to """
                    _profile_name: solid
                    _profile_description: "SOLID rules"
                    _profile_axis: capability
                """.trimIndent(),
            ),
        )

        val catalog = SchemaService(registry).catalog()

        // Order: by axis (language → framework → capability), then by name within axis.
        assertEquals(
            listOf(
                ProfileEntry("kotlin-gradle", "language", "Kotlin + Gradle (KTS)"),
                ProfileEntry("typescript-pnpm", "language", "TypeScript + pnpm baseline"),
                ProfileEntry("nextjs", "framework", "Next.js"),
                ProfileEntry("solid", "capability", "SOLID rules"),
            ),
            catalog.profiles,
        )
    }

    @Test
    fun skipsProfilesWithMismatchedAxis() {
        // File lives under language/ but declares capability — the bundle is
        // broken. Schema command must still succeed and silently omit the file
        // from the catalog.
        val registry = InMemoryTemplateRegistry(
            mapOf(
                "profiles/language/imposter.yaml" to """
                    _profile_name: imposter
                    _profile_description: "wrong axis"
                    _profile_axis: capability
                """.trimIndent(),
                "profiles/language/typescript-pnpm.yaml" to """
                    _profile_name: typescript-pnpm
                    _profile_description: "TS"
                    _profile_axis: language
                """.trimIndent(),
            ),
        )

        val catalog = SchemaService(registry).catalog()
        assertEquals(
            listOf(ProfileEntry("typescript-pnpm", "language", "TS")),
            catalog.profiles,
        )
    }

    @Test
    fun ignoresFilesNotShapedAsAxisProfile() {
        val registry = InMemoryTemplateRegistry(
            mapOf(
                "profiles/README.md" to "",
                "profiles/profile.schema.json" to "",
                // Wrong extension.
                "profiles/language/notes.txt" to "",
                // Unknown axis directory.
                "profiles/host/claude-code.yaml" to """
                    _profile_name: claude-code
                    _profile_axis: host
                """.trimIndent(),
                // Nested deeper than `<axis>/<name>.yaml`.
                "profiles/language/nested/inner.yaml" to "",
            ),
        )

        val catalog = SchemaService(registry).catalog()
        assertTrue(catalog.profiles.isEmpty())
    }

    @Test
    fun ignoresPromptsWithUnexpectedShape() {
        val registry = InMemoryTemplateRegistry(
            mapOf(
                "prompts/Architect.md" to "",
                "prompts/Weird.too.many.dots.md" to "",
                "prompts/sub/nested.md" to "",
                "prompts/no-extension" to "",
            ),
        )

        val catalog = SchemaService(registry).catalog()

        assertEquals(listOf("Architect"), catalog.agents)
        assertTrue(catalog.agentDialectVariants.isEmpty())
    }

    @Test
    fun emptyRegistryProducesEmptyLists() {
        val catalog = SchemaService(InMemoryTemplateRegistry(emptyMap())).catalog()

        assertTrue(catalog.agents.isEmpty())
        assertTrue(catalog.agentDialectVariants.isEmpty())
        assertTrue(catalog.commands.isEmpty())
        assertTrue(catalog.skills.isEmpty())
        assertTrue(catalog.promptDialects.isEmpty())
        assertTrue(catalog.targetAdapters.isEmpty())
        assertTrue(catalog.knowledgeSections.isEmpty())
        assertTrue(catalog.sharedSnippets.isEmpty())
        assertTrue(catalog.rules.isEmpty())
        assertTrue(catalog.userPrompts.isEmpty())
        assertTrue(catalog.profiles.isEmpty())
        // profileAxes is hardcoded — every catalog reports the contract even
        // when no profiles ship with this binary.
        assertEquals(3, catalog.profileAxes.size)
        // Enums are independent of the bundle — same shape regardless of templates.
        assertEquals(5, catalog.enums.size)
    }
}

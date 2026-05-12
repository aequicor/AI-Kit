package com.aikit.setup.validation

import com.aikit.setup.manifest.BlockYamlParser
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.validation.rules.AgentPromptPresentRule
import com.aikit.setup.validation.rules.ManifestVersionRule
import com.aikit.setup.validation.rules.ModelProviderExistsRule
import com.aikit.setup.validation.rules.OrchestratorUnicityRule
import com.aikit.setup.validation.rules.ProjectSlugRule
import com.aikit.setup.validation.rules.ProviderAuthRule
import com.aikit.setup.validation.rules.RenderTargetsExistRule
import com.aikit.setup.validation.rules.RequiredTopLevelKeysRule
import com.aikit.setup.validation.rules.TargetProviderExistsRule
import com.aikit.setup.validation.rules.UniqueIdsRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuralRulesTest {

    private val parser = BlockYamlParser()

    private fun parse(yaml: String): Manifest = Manifest(parser.parse(yaml.trimIndent()))

    @Test
    fun requiredTopLevelKeysFlagsMissing() {
        val errors = RequiredTopLevelKeysRule().check(
            parse(
                """
                manifest_version: "1.0.0"
                project: { name: x, slug: x }
                """,
            ),
        )
        // targets, providers, models, agents — 4 missing keys.
        assertEquals(4, errors.size)
        assertTrue(errors.all { it.code == "missing_required_key" })
    }

    @Test
    fun rejectsNonMappingRoot() {
        // Sequence-rooted document is the only non-mapping shape the parser
        // emits in practice; the rule should still reject it.
        val errors = RequiredTopLevelKeysRule().check(
            parse(
                """
                - a
                - b
                """,
            ),
        )
        assertEquals(listOf("manifest_root_not_mapping"), errors.map { it.code })
    }

    @Test
    fun manifestVersionRejectsNonSemver() {
        val errors = ManifestVersionRule().check(parse("manifest_version: not-semver"))
        assertEquals(listOf("invalid_manifest_version"), errors.map { it.code })
    }

    @Test
    fun manifestVersionAcceptsSemver() {
        val errors = ManifestVersionRule().check(parse("manifest_version: \"1.2.3\""))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun projectSlugRejectsUppercase() {
        val errors = ProjectSlugRule().check(
            parse("project: { name: x, slug: BadSlug }"),
        )
        assertEquals(listOf("invalid_project_slug"), errors.map { it.code })
    }

    @Test
    fun projectSlugAcceptsKebabCase() {
        val errors = ProjectSlugRule().check(
            parse("project: { name: x, slug: my-project-1 }"),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun uniqueIdsRuleFlagsDuplicates() {
        val errors = UniqueIdsRule().check(
            parse(
                """
                models:
                  - { id: a, provider: p, model: x, family: f }
                  - { id: a, provider: p, model: y, family: f }
                """,
            ),
        )
        assertEquals(listOf("duplicate_id"), errors.map { it.code })
    }

    @Test
    fun renderTargetsExistRuleFlagsTypos() {
        val errors = RenderTargetsExistRule().check(
            parse(
                """
                targets:
                  - { id: claude-code, native_provider: anthropic }
                render_targets: [claude_code, cursor]
                """,
            ),
        )
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.code == "unknown_render_target" })
    }

    @Test
    fun targetProviderExistsRuleFlagsUnknownNativeProvider() {
        val errors = TargetProviderExistsRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic }
                targets:
                  - { id: claude-code, native_provider: anthropc, adapter: claude-code }
                """,
            ),
        )
        assertEquals(listOf("unknown_native_provider"), errors.map { it.code })
        assertEquals(listOf("/targets/0/native_provider"), errors.map { it.path })
    }

    @Test
    fun targetProviderExistsRuleAcceptsAnySentinel() {
        val errors = TargetProviderExistsRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic }
                targets:
                  - { id: aider, native_provider: any, adapter: aider }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun targetProviderExistsRuleFlagsUnknownCanUseVia() {
        val errors = TargetProviderExistsRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic }
                targets:
                  - id: aider
                    native_provider: any
                    can_use_via: [openai, anthropic]
                """,
            ),
        )
        assertEquals(listOf("unknown_native_provider"), errors.map { it.code })
        assertEquals(listOf("/targets/0/can_use_via/0"), errors.map { it.path })
    }

    @Test
    fun modelProviderExistsRuleFlagsUnknown() {
        val errors = ModelProviderExistsRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic }
                models:
                  - { id: gpt5, provider: openai, model: gpt-5, family: openai }
                  - { id: opus, provider: anthropic, model: claude-opus-4-7, family: anthropic }
                """,
            ),
        )
        assertEquals(listOf("unknown_provider"), errors.map { it.code })
        assertEquals(listOf("/models/0/provider"), errors.map { it.path })
    }

    @Test
    fun agentPromptPresentRuleFlagsMissingDefault() {
        val errors = AgentPromptPresentRule().check(
            parse(
                """
                agents:
                  - id: A
                    prompt:
                      anthropic: { include: prompts/A.anthropic.md }
                """,
            ),
        )
        assertEquals(listOf("missing_default_prompt_body"), errors.map { it.code })
    }

    @Test
    fun providerAuthRuleAcceptsSubscription() {
        val errors = ProviderAuthRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic, auth: subscription }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun providerAuthRuleRequiresApiKeyEnvForApiKeyAuth() {
        val errors = ProviderAuthRule().check(
            parse(
                """
                providers:
                  - { id: openai, kind: openai, auth: api_key }
                """,
            ),
        )
        assertEquals(listOf("missing_api_key_env"), errors.map { it.code })
    }

    @Test
    fun providerAuthRuleDefaultsToApiKey() {
        val errors = ProviderAuthRule().check(
            parse(
                """
                providers:
                  - { id: openai, kind: openai }
                """,
            ),
        )
        assertEquals(listOf("missing_api_key_env"), errors.map { it.code })
    }

    @Test
    fun providerAuthRuleRejectsUnknownAuth() {
        val errors = ProviderAuthRule().check(
            parse(
                """
                providers:
                  - { id: anthropic, kind: anthropic, auth: bearer }
                """,
            ),
        )
        assertEquals(listOf("unknown_provider_auth"), errors.map { it.code })
    }

    @Test
    fun providerAuthRuleAcceptsNoneForLocalBackend() {
        val errors = ProviderAuthRule().check(
            parse(
                """
                providers:
                  - { id: local, kind: ollama, auth: none }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun agentPromptPresentRuleAcceptsShortForm() {
        val errors = AgentPromptPresentRule().check(
            parse(
                """
                agents:
                  - id: A
                    prompt: { include: prompts/A.md }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun orchestratorUnicityAcceptsSingleExplicit() {
        val errors = OrchestratorUnicityRule().check(
            parse(
                """
                agents:
                  - { id: Driver, role: orchestrator }
                  - { id: Helper }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun orchestratorUnicityAcceptsLegacyMainShorthand() {
        // `id: Main` with no role is auto-promoted; this single-agent setup
        // is still valid even without an explicit `role:` field.
        val errors = OrchestratorUnicityRule().check(
            parse(
                """
                agents:
                  - { id: Main }
                  - { id: Researcher }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun orchestratorUnicityRejectsTwoExplicit() {
        val errors = OrchestratorUnicityRule().check(
            parse(
                """
                agents:
                  - { id: A, role: orchestrator }
                  - { id: B, role: orchestrator }
                """,
            ),
        )
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.code == "multiple_orchestrators" })
        assertEquals(listOf("/agents/0/role", "/agents/1/role"), errors.map { it.path })
    }

    @Test
    fun orchestratorUnicityRejectsLegacyMainPlusExplicit() {
        // Coexisting back-compat `Main` and an explicit orchestrator is the
        // ambiguous case the rule is designed to catch — the renderer cannot
        // decide whose body to inline.
        val errors = OrchestratorUnicityRule().check(
            parse(
                """
                agents:
                  - { id: Main }
                  - { id: Driver, role: orchestrator }
                """,
            ),
        )
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.code == "multiple_orchestrators" })
    }

    @Test
    fun orchestratorUnicityIgnoresSubagentRole() {
        // Explicit `role: subagent` is the default and must not count toward
        // the orchestrator pool, even when paired with a legacy `id: Main`
        // somewhere else.
        val errors = OrchestratorUnicityRule().check(
            parse(
                """
                agents:
                  - { id: Main, role: orchestrator }
                  - { id: Researcher, role: subagent }
                  - { id: Verifier }
                """,
            ),
        )
        assertTrue(errors.isEmpty())
    }
}

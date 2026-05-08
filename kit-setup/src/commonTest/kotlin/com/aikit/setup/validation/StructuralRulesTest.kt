package com.aikit.setup.validation

import com.aikit.setup.manifest.BlockYamlParser
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.validation.rules.AgentPromptPresentRule
import com.aikit.setup.validation.rules.ManifestVersionRule
import com.aikit.setup.validation.rules.ModelProviderExistsRule
import com.aikit.setup.validation.rules.ProjectSlugRule
import com.aikit.setup.validation.rules.ProviderAuthRule
import com.aikit.setup.validation.rules.RenderTargetsExistRule
import com.aikit.setup.validation.rules.RequiredTopLevelKeysRule
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
}

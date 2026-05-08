package com.aikit.setup.validation

import com.aikit.setup.generation.PackageLoader
import com.aikit.setup.manifest.BlockYamlParser
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.templates.InMemoryTemplateRegistry
import com.aikit.setup.validation.rules.ResolvableModelsRule
import com.aikit.setup.validation.rules.TargetCollisionRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageAwareRulesTest {

    private val parser = BlockYamlParser()
    private fun parse(yaml: String): Manifest = Manifest(parser.parse(yaml.trimIndent()))

    private val claudeAdapter = """
        adapter_version: "1.0.0"
        id: claude-code
        config_dir: ".claude"
        instruction_file: "CLAUDE.md"
        settings_file: ".claude/settings.json"
        artifact_paths:
          agent: ".claude/agents/{id}.md"
        capabilities:
          subagents: true
    """.trimIndent()

    private val opencodeAdapter = """
        adapter_version: "1.0.0"
        id: opencode
        config_dir: ".opencode"
        instruction_file: "AGENTS.md"
        settings_file: "opencode.json"
        artifact_paths:
          agent: ".opencode/agents/{id}.md"
        capabilities:
          subagents: true
    """.trimIndent()

    private val qwenAdapter = """
        adapter_version: "1.0.0"
        id: qwen-code
        config_dir: ".qwen"
        instruction_file: "AGENTS.md"
        settings_file: ".qwen/settings.json"
        artifact_paths:
          agent: ".qwen/agents/{id}.md"
        capabilities:
          subagents: true
    """.trimIndent()

    private val cursorAdapter = """
        adapter_version: "1.0.0"
        id: cursor
        instruction_file: ".cursor/rules"
        settings_file: ".cursor/mcp.json"
        artifact_paths:
          agent: null
          rule: ".cursor/rules/{id}.mdc"
        capabilities:
          subagents: false
    """.trimIndent()

    private fun loaderFor(map: Map<String, String>): PackageLoader =
        PackageLoader(InMemoryTemplateRegistry(map), parser)

    // ── TargetCollisionRule ─────────────────────────────────────────────────

    @Test
    fun collisionRuleFlagsAgentsMdConflict() {
        val templates = mapOf(
            "target_adapters/opencode/adapter.yaml" to opencodeAdapter,
            "target_adapters/qwen-code/adapter.yaml" to qwenAdapter,
        )
        val rule = TargetCollisionRule(loaderFor(templates))
        val manifest = parse(
            """
            targets:
              - { id: opencode, native_provider: any }
              - { id: qwen-code, native_provider: any }
            target_adapters:
              - { id: opencode, path: ./target_adapters/opencode }
              - { id: qwen-code, path: ./target_adapters/qwen-code }
            render_targets: [opencode, qwen-code]
            """,
        )
        val errors = rule.check(manifest)
        assertEquals(1, errors.size)
        assertEquals("target_output_collision", errors[0].code)
        assertTrue("AGENTS.md" in errors[0].message)
    }

    @Test
    fun collisionRulePassesWhenAdaptersUseDistinctFiles() {
        val templates = mapOf(
            "target_adapters/claude-code/adapter.yaml" to claudeAdapter,
            "target_adapters/cursor/adapter.yaml" to cursorAdapter,
        )
        val rule = TargetCollisionRule(loaderFor(templates))
        val manifest = parse(
            """
            targets:
              - { id: claude-code, native_provider: anthropic }
              - { id: cursor, native_provider: anthropic }
            target_adapters:
              - { id: claude-code, path: ./target_adapters/claude-code }
              - { id: cursor, path: ./target_adapters/cursor }
            render_targets: [claude-code, cursor]
            """,
        )
        assertTrue(rule.check(manifest).isEmpty())
    }

    // ── ResolvableModelsRule ────────────────────────────────────────────────

    @Test
    fun resolvableRuleFlagsAgentWithUnsatisfiableNeeds() {
        val templates = mapOf(
            "target_adapters/claude-code/adapter.yaml" to claudeAdapter,
        )
        val rule = ResolvableModelsRule(loaderFor(templates))
        val manifest = parse(
            """
            targets:
              - { id: claude-code, native_provider: anthropic }
            target_adapters:
              - { id: claude-code, path: ./target_adapters/claude-code }
            render_targets: [claude-code]
            providers:
              - { id: anthropic, kind: anthropic }
            models:
              - id: opus
                provider: anthropic
                model: claude-opus-4-7
                family: anthropic
                tier: reasoner
                capabilities: [code]
            agents:
              - id: SecurityReviewer
                description: ""
                model_selection:
                  needs: [reasoning, code, vision]
                prompt: { include: prompts/x.md }
            """,
        )
        val errors = rule.check(manifest)
        assertEquals(listOf("unresolvable_model"), errors.map { it.code })
    }

    @Test
    fun resolvableRuleSkipsTargetsWithoutSubagentSupport() {
        val templates = mapOf(
            "target_adapters/cursor/adapter.yaml" to cursorAdapter,
        )
        val rule = ResolvableModelsRule(loaderFor(templates))
        val manifest = parse(
            """
            targets:
              - { id: cursor, native_provider: anthropic }
            target_adapters:
              - { id: cursor, path: ./target_adapters/cursor }
            render_targets: [cursor]
            providers:
              - { id: anthropic, kind: anthropic }
            models:
              - { id: opus, provider: anthropic, model: claude-opus-4-7, family: anthropic, tier: reasoner, capabilities: [code] }
            agents:
              - id: A
                description: ""
                model_selection: { needs: [unsatisfiable-cap] }
                prompt: { include: prompts/x.md }
            """,
        )
        // Cursor doesn't render subagents, so the resolver skips this combo entirely.
        assertTrue(rule.check(manifest).isEmpty())
    }

    @Test
    fun resolvableRuleHonorsTargetsOverride() {
        val templates = mapOf(
            "target_adapters/claude-code/adapter.yaml" to claudeAdapter,
            "target_adapters/opencode/adapter.yaml" to opencodeAdapter,
        )
        val rule = ResolvableModelsRule(loaderFor(templates))
        val manifest = parse(
            """
            targets:
              - { id: claude-code, native_provider: anthropic }
              - { id: opencode, native_provider: any }
            target_adapters:
              - { id: claude-code, path: ./target_adapters/claude-code }
              - { id: opencode, path: ./target_adapters/opencode }
            render_targets: [claude-code, opencode]
            providers:
              - { id: anthropic, kind: anthropic }
            models:
              - { id: opus, provider: anthropic, model: claude-opus-4-7, family: anthropic, tier: reasoner, capabilities: [code] }
            agents:
              - id: A
                description: ""
                model_selection: { needs: [unsatisfiable-cap] }
                targets_override: [claude-code]
                prompt: { include: prompts/x.md }
            """,
        )
        // Agent declares targets_override = [claude-code] — opencode is excluded.
        // The unresolvable error should fire only for claude-code, not opencode.
        val errors = rule.check(manifest)
        assertEquals(1, errors.size)
        assertTrue("claude-code" in errors[0].message)
    }
}

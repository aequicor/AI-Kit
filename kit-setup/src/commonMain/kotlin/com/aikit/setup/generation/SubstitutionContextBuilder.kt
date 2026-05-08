package com.aikit.setup.generation

import com.aikit.setup.manifest.TypedClaudeCode
import com.aikit.setup.manifest.TypedManifest
import com.aikit.setup.manifest.TypedModels
import com.aikit.setup.manifest.TypedModule
import com.aikit.setup.manifest.TypedProvider
import com.aikit.setup.manifest.TypedUi
import com.aikit.setup.profile.HostMetadata

/**
 * Builds the substitution variable map consumed by [TemplateRenderer]
 * from a fully decoded [TypedManifest] plus a chosen host.
 *
 * Mirrors the variable table in `docs/prompts/setup.md` (PHASE 3.1) so the
 * binary writes exactly the same files the prompt-driven workflow would.
 */
class SubstitutionContextBuilder(private val now: () -> String = { "(unset)" }) {

    fun build(manifest: TypedManifest, hostName: String, host: HostMetadata, kitRepo: String): Map<String, String> {
        val base = baseVariables(manifest, kitRepo)
        val overlay = hostOverlay(manifest, hostName, host)
        return base + overlay
    }

    private fun baseVariables(m: TypedManifest, kitRepo: String): Map<String, String> {
        val timestamp = now()
        val mcp = m.mcp
        return buildMap {
            put("KIT_REPO", kitRepo)
            put("KIT_LANG", m.languageCode)
            put("VAULT_PATH", m.vaultPath.trimEnd('/'))
            put("PROJECT_NAME", m.project.name)
            put("PROJECT_DESCRIPTION", m.project.description)
            put("STACK_DESCRIPTION", "${m.project.name} — ${m.stack.language} stack")
            put("BUILD_COMMAND", m.stack.buildCommand)
            put("COMPILE_COMMAND", m.stack.compileCommand)
            put("LINT_COMMAND", m.stack.lintCommand)
            put("TEST_COMMAND_TEMPLATE", m.stack.testCommand)
            put("MODULE_NAMES_LIST", m.modules.joinToString(" / ") { it.name })
            put("MODULE_TABLE", renderModuleTable(m.modules))
            put("MODULE_SOURCE_TABLE", renderSourceTable(m.modules))
            put("MODULE_TEST_TABLE", renderTestTable(m.modules))
            put("MODULE_BUILD_COMMANDS", renderBuildCommands(m.modules, m.stack.buildCommand))
            put("MODULE_DOCS_LIST", m.modules.joinToString("\n") { "- ${it.docsPath}" })
            put("ISO_TIMESTAMP_PLACEHOLDER", timestamp)
            put("CONTEXT7_ENABLED", boolToString(mcp.context7.enabled))
            put("KNOWLEDGE_ENABLED", boolToString(mcp.knowledge.enabled))
            put("SERENA_ENABLED", boolToString(mcp.serena.enabled))
            put("CONTEXT7_API_KEY_ENV", mcp.context7.apiKeyEnv)
            put("KNOWLEDGE_URL", mcp.knowledge.url)
            put("LSP_BLOCK", renderLspBlock(m))
            put("UI_FRAMEWORK", m.ui.framework ?: "")
            put("PLATFORMS", m.ui.platforms.joinToString(", "))
            put("COLOR_TABLE", renderColorTable(m.ui))
            put("FORBIDDEN_PATTERNS_LIST", m.codeQuality.forbiddenPatterns.joinToString("\n") { "- $it" })
            put("FORMATTER_BLOCK", renderFormatterBlock(m))
            put("DEPENDENCY_FILES_LIST", dependencyFilesList(m.stack.language))
        }
    }

    private fun hostOverlay(m: TypedManifest, hostName: String, host: HostMetadata): Map<String, String> {
        val overlay = mutableMapOf<String, String>()
        overlay["HOST_DIR"] = host.templateDir
        overlay["HOST_NAME"] = if (hostName == "claude-code") "Claude Code" else "OpenCode"
        overlay["HOST_INSTRUCTION_FILE"] = host.instructionFile
        overlay["HOST_CONFIG_FILE"] = host.configFile
        overlay["KIT_LANG_ENV"] = if (hostName == "claude-code") "KIT_LANG" else "OPENCODE_LANG"
        overlay["DISPATCH_TOOL"] = if (hostName == "claude-code") "the Agent tool" else "`task`"
        overlay["DISPATCH_TOOL_DESC"] = if (hostName == "claude-code")
            "the Agent tool with `subagent_type=<AgentName>`"
        else
            "the `task` tool — `task @AgentName \"<args>\"`"

        // Provider/model variables.
        when (hostName) {
            "opencode" -> applyOpencodeProviderOverlay(overlay, m.provider, m.models)
            "claude-code" -> applyClaudeCodeOverlay(overlay, m.claudeCode)
        }

        // Multi-host builds also need the MCP_SERVERS_BLOCK for Claude Code's .mcp.json.
        overlay["MCP_SERVERS_BLOCK"] = if (hostName == "claude-code") renderMcpServersBlock(m) else ""
        return overlay
    }

    private fun applyOpencodeProviderOverlay(
        overlay: MutableMap<String, String>,
        provider: TypedProvider?,
        models: TypedModels?,
    ) {
        val p = provider
        if (p != null) {
            overlay["PROVIDER_ID"] = p.name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
            overlay["PROVIDER_NAME"] = p.name
            overlay["PROVIDER_BASE_URL"] = p.baseUrl
            overlay["PROVIDER_API_KEY_ENV"] = p.apiKeyEnv
        }
        applyModelOverlay(overlay, models)
    }

    private fun applyClaudeCodeOverlay(overlay: MutableMap<String, String>, cc: TypedClaudeCode?) {
        applyModelOverlay(overlay, cc?.models)
    }

    private fun applyModelOverlay(overlay: MutableMap<String, String>, models: TypedModels?) {
        if (models == null) return
        overlay["DEFAULT_MODEL"] = models.default
        overlay["CODER_MODEL"] = models.coder
        overlay["REVIEWER_MODEL"] = models.reviewer
        overlay["DESIGNER_MODEL"] = models.designer ?: models.coder
        overlay["SMALL_MODEL"] = models.small.ifEmpty { models.coder }
    }

    private fun boolToString(b: Boolean): String = if (b) "true" else "false"

    private fun renderModuleTable(modules: List<TypedModule>): String {
        val header = "| Module | Gradle module | Docs | Responsibility |\n" +
            "|--------|---------------|------|----------------|"
        val rows = modules.joinToString("\n") { m ->
            val gradle = m.gradleModule?.let { "`$it`" } ?: "—"
            val docs = ensureTrailingSlash(m.docsPath)
            "| `${m.name}` | $gradle | $docs | ${m.responsibility} |"
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun renderSourceTable(modules: List<TypedModule>): String {
        val header = "| Module | Gradle task | Source root |\n" +
            "|--------|-------------|-------------|"
        val rows = modules.joinToString("\n") { m ->
            val gradle = m.gradleModule?.let { "`$it`" } ?: "—"
            "| ${m.name} | $gradle | `${m.sourceRoot}` |"
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun renderTestTable(modules: List<TypedModule>): String {
        val header = "| Module | Test root |\n|--------|----------|"
        val rows = modules.joinToString("\n") { m -> "| `${m.name}` | `${m.testRoot}` |" }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun renderBuildCommands(modules: List<TypedModule>, buildCommand: String): String {
        return modules.joinToString("\n") { m ->
            if (m.gradleModule != null) {
                "$buildCommand ${m.gradleModule}:build"
            } else {
                "# build command for ${m.name}: $buildCommand build"
            }
        }
    }

    private fun renderColorTable(ui: TypedUi): String {
        val header = "| Color | HEX | Purpose |\n|-------|-----|---------|"
        if (ui.colors.isEmpty()) {
            return "$header\n| (configure via the Designer agent) | `#000000` | (none yet) |"
        }
        val rows = ui.colors.joinToString("\n") { c -> "| ${c.name} | `${c.hex}` | ${c.purpose} |" }
        return "$header\n$rows"
    }

    private fun renderLspBlock(m: TypedManifest): String {
        if (!m.lsp.enabled) return ""
        val lang = m.lsp.command.removeSuffix("-lsp").removeSuffix("_lsp").ifEmpty { "default" }
        val ext = m.lsp.extensions.joinToString(", ") { "\"$it\"" }
        return "  \"lsp\": {\n" +
            "    \"$lang\": {\n" +
            "      \"command\": [\"${m.lsp.command}\"],\n" +
            "      \"extensions\": [$ext]\n" +
            "    }\n" +
            "  },"
    }

    private fun renderFormatterBlock(m: TypedManifest): String {
        if (!m.formatter.enabled) return ""
        val cmd = m.formatter.command.joinToString(", ") { "\"$it\"" }
        val ext = m.formatter.extensions.joinToString(", ") { "\"$it\"" }
        return "\"formatter\": {\n" +
            "    \"${m.formatter.name}\": {\n" +
            "      \"command\": [$cmd],\n" +
            "      \"extensions\": [$ext]\n" +
            "    }\n" +
            "  },"
    }

    private fun renderMcpServersBlock(m: TypedManifest): String {
        val parts = mutableListOf<String>()
        if (m.mcp.context7.enabled) {
            parts += "    \"context7\": { \"type\": \"http\", \"url\": \"https://mcp.context7.com/mcp\", " +
                "\"headers\": { \"CONTEXT7_API_KEY\": \"\${" + m.mcp.context7.apiKeyEnv + "}\" } }"
        }
        if (m.mcp.knowledge.enabled) {
            parts += "    \"knowledge-my-app\": { \"type\": \"http\", \"url\": \"${m.mcp.knowledge.url}\" }"
        }
        if (m.mcp.serena.enabled) {
            parts += "    \"serena\": { \"type\": \"stdio\", \"command\": \"serena\", \"args\": [\"start-mcp-server\"] }"
        }
        if (parts.isEmpty()) return ""
        return "{\n" + parts.joinToString(",\n") + "\n  }"
    }

    private fun dependencyFilesList(language: String): String = when (language) {
        "kotlin", "java" -> "gradle/libs.versions.toml or build.gradle.kts"
        "python" -> "requirements.txt / pyproject.toml"
        "typescript" -> "package.json"
        "go" -> "go.mod"
        "rust" -> "Cargo.toml"
        else -> "Primary dependency manifest"
    }

    private fun ensureTrailingSlash(s: String): String = if (s.endsWith("/")) s else "$s/"
}

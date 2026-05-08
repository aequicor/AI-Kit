package com.aikit.setup.manifest

/**
 * Strongly-typed view of an ai-agent-kit manifest.
 *
 * The [DefaultManifestLoader] always produces a [Manifest] containing the raw
 * YAML tree; this typed view is decoded best-effort by [TypedManifestDecoder]
 * and consumed by the generator after validation has passed. Optional fields
 * fall back to schema defaults so the generator never has to second-guess
 * missing keys; required fields default to safe sentinels (empty strings /
 * empty lists) and the validator catches them before generation runs.
 */
data class TypedManifest(
    val kitVersion: String,
    val languageCode: String,
    val hosts: List<String>,
    val vaultPath: String,
    val project: TypedProject,
    val stack: TypedStack,
    val modules: List<TypedModule>,
    val provider: TypedProvider?,
    val models: TypedModels?,
    val claudeCode: TypedClaudeCode?,
    val mcp: TypedMcp,
    val lsp: TypedLsp,
    val ui: TypedUi,
    val codeQuality: TypedCodeQuality,
    val formatter: TypedFormatter,
)

data class TypedProject(val name: String, val description: String)

data class TypedStack(
    val language: String,
    val profiles: List<String>,
    val externalProfiles: Map<String, String>,
    val buildCommand: String,
    val compileCommand: String,
    val lintCommand: String,
    val testCommand: String,
)

data class TypedModule(
    val name: String,
    val gradleModule: String?,
    val sourceRoot: String,
    val testRoot: String,
    val docsPath: String,
    val responsibility: String,
    val conventions: String,
    val moduleDependencies: String,
)

data class TypedProvider(val name: String, val baseUrl: String, val apiKeyEnv: String)

data class TypedModels(
    val default: String,
    val coder: String,
    val reviewer: String,
    val designer: String?,
    val small: String,
)

data class TypedClaudeCode(val models: TypedModels)

data class TypedMcp(
    val context7: TypedContext7,
    val knowledge: TypedKnowledge,
    val serena: TypedSerena,
)

data class TypedContext7(val enabled: Boolean, val apiKeyEnv: String)
data class TypedKnowledge(val enabled: Boolean, val url: String)
data class TypedSerena(val enabled: Boolean)

data class TypedLsp(val enabled: Boolean, val command: String, val extensions: List<String>)

data class TypedUi(
    val framework: String?,
    val platforms: List<String>,
    val colors: List<TypedColor>,
)

data class TypedColor(val name: String, val hex: String, val purpose: String)

data class TypedCodeQuality(val forbiddenPatterns: List<String>)

data class TypedFormatter(
    val enabled: Boolean,
    val name: String,
    val command: List<String>,
    val extensions: List<String>,
)

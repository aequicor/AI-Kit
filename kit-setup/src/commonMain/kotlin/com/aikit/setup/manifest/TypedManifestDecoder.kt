package com.aikit.setup.manifest

/**
 * Decodes a [RawNode] tree into a [TypedManifest].
 *
 * The decoder is permissive on purpose: it never throws. Missing or
 * structurally-wrong fields collapse to documented defaults so the validator
 * can keep operating on the raw tree to report stable, path-keyed errors,
 * and the generator (which only runs after validation passes) gets a fully
 * populated typed view.
 */
object TypedManifestDecoder {

    fun decode(root: RawNode): TypedManifest {
        val map = (root as? RawNode.Mapping)?.entries.orEmpty()
        return TypedManifest(
            kitVersion = scalar(map["kit_version"]) ?: "",
            languageCode = scalar(map["language_code"]) ?: "en",
            hosts = stringList(map["hosts"]),
            vaultPath = scalar(map["vault_path"]) ?: "vault",
            project = decodeProject(map["project"]),
            stack = decodeStack(map["stack"]),
            modules = (map["modules"] as? RawNode.Sequence)?.items
                ?.mapNotNull { decodeModule(it) }
                .orEmpty(),
            provider = decodeProvider(map["provider"]),
            models = decodeModels(map["models"]),
            claudeCode = decodeClaudeCode(map["claude_code"]),
            mcp = decodeMcp(map["mcp"]),
            lsp = decodeLsp(map["lsp"]),
            ui = decodeUi(map["ui"]),
            codeQuality = decodeCodeQuality(map["code_quality"]),
            formatter = decodeFormatter(map["formatter"]),
        )
    }

    private fun decodeProject(node: RawNode?): TypedProject {
        val map = mapOf(node)
        return TypedProject(
            name = scalar(map["name"]) ?: "",
            description = scalar(map["description"]) ?: "",
        )
    }

    private fun decodeStack(node: RawNode?): TypedStack {
        val map = mapOf(node)
        val externalProfilesNode = map["external_profiles"] as? RawNode.Mapping
        val externalProfiles = externalProfilesNode?.entries
            ?.mapValues { scalar(it.value) ?: "" }
            ?.filterValues { it.isNotEmpty() }
            .orEmpty()
        return TypedStack(
            language = scalar(map["language"]) ?: "",
            profiles = stringList(map["profiles"]),
            externalProfiles = externalProfiles,
            buildCommand = scalar(map["build_command"]) ?: "",
            compileCommand = scalar(map["compile_command"]) ?: "",
            lintCommand = scalar(map["lint_command"]) ?: "",
            testCommand = scalar(map["test_command"]) ?: "",
        )
    }

    private fun decodeModule(node: RawNode): TypedModule? {
        val map = (node as? RawNode.Mapping)?.entries ?: return null
        val name = scalar(map["name"]) ?: return null
        return TypedModule(
            name = name,
            gradleModule = scalar(map["gradle_module"]).takeUnless { it.isNullOrBlank() || it == "null" },
            sourceRoot = scalar(map["source_root"]) ?: "",
            testRoot = scalar(map["test_root"]) ?: "",
            docsPath = scalar(map["docs_path"]) ?: "",
            responsibility = scalar(map["responsibility"]) ?: "",
            conventions = scalar(map["conventions"]) ?: "",
            moduleDependencies = scalar(map["module_dependencies"]) ?: "",
        )
    }

    private fun decodeProvider(node: RawNode?): TypedProvider? {
        val map = (node as? RawNode.Mapping)?.entries ?: return null
        if (map.isEmpty()) return null
        return TypedProvider(
            name = scalar(map["name"]) ?: "",
            baseUrl = scalar(map["base_url"]) ?: "",
            apiKeyEnv = scalar(map["api_key_env"]) ?: "",
        )
    }

    private fun decodeModels(node: RawNode?): TypedModels? {
        val map = (node as? RawNode.Mapping)?.entries ?: return null
        if (map.isEmpty()) return null
        val coder = scalar(map["coder"]) ?: ""
        return TypedModels(
            default = scalar(map["default"]) ?: "",
            coder = coder,
            reviewer = scalar(map["reviewer"]) ?: "",
            designer = scalar(map["designer"]).takeUnless { it.isNullOrBlank() || it == "null" || map["designer"] is RawNode.Null },
            small = scalar(map["small"]) ?: coder,
        )
    }

    private fun decodeClaudeCode(node: RawNode?): TypedClaudeCode? {
        val map = (node as? RawNode.Mapping)?.entries ?: return null
        val models = decodeModels(map["models"]) ?: return null
        return TypedClaudeCode(models)
    }

    private fun decodeMcp(node: RawNode?): TypedMcp {
        val map = mapOf(node)
        return TypedMcp(
            context7 = (map["context7"] as? RawNode.Mapping)?.entries.let { c ->
                TypedContext7(
                    enabled = bool(c?.get("enabled")) ?: true,
                    apiKeyEnv = scalar(c?.get("api_key_env")) ?: "CONTEXT7_API_KEY",
                )
            },
            knowledge = (map["knowledge"] as? RawNode.Mapping)?.entries.let { c ->
                TypedKnowledge(
                    enabled = bool(c?.get("enabled")) ?: true,
                    url = scalar(c?.get("url")) ?: "http://localhost:8085/mcp",
                )
            },
            serena = (map["serena"] as? RawNode.Mapping)?.entries.let { c ->
                TypedSerena(enabled = bool(c?.get("enabled")) ?: false)
            },
        )
    }

    private fun decodeLsp(node: RawNode?): TypedLsp {
        val map = mapOf(node)
        return TypedLsp(
            enabled = bool(map["enabled"]) ?: false,
            command = scalar(map["command"]) ?: "",
            extensions = stringList(map["extensions"]),
        )
    }

    private fun decodeUi(node: RawNode?): TypedUi {
        val map = mapOf(node)
        val frameworkRaw = map["framework"]
        val framework = scalar(frameworkRaw).takeUnless { it.isNullOrBlank() || it == "null" || frameworkRaw is RawNode.Null }
        val colors = (map["colors"] as? RawNode.Sequence)?.items
            ?.mapNotNull { item ->
                val cm = (item as? RawNode.Mapping)?.entries ?: return@mapNotNull null
                TypedColor(
                    name = scalar(cm["name"]) ?: return@mapNotNull null,
                    hex = scalar(cm["hex"]) ?: return@mapNotNull null,
                    purpose = scalar(cm["purpose"]) ?: "",
                )
            }
            .orEmpty()
        return TypedUi(framework = framework, platforms = stringList(map["platforms"]), colors = colors)
    }

    private fun decodeCodeQuality(node: RawNode?): TypedCodeQuality {
        val map = mapOf(node)
        return TypedCodeQuality(forbiddenPatterns = stringList(map["forbidden_patterns"]))
    }

    private fun decodeFormatter(node: RawNode?): TypedFormatter {
        val map = mapOf(node)
        return TypedFormatter(
            enabled = bool(map["enabled"]) ?: false,
            name = scalar(map["name"]) ?: "",
            command = stringList(map["command"]),
            extensions = stringList(map["extensions"]),
        )
    }

    private fun mapOf(node: RawNode?): Map<String, RawNode> =
        (node as? RawNode.Mapping)?.entries.orEmpty()

    private fun scalar(node: RawNode?): String? = when (node) {
        is RawNode.Scalar -> node.value
        else -> null
    }

    private fun bool(node: RawNode?): Boolean? = when (val s = scalar(node)?.lowercase()) {
        null -> null
        "true", "yes", "on" -> true
        "false", "no", "off" -> false
        else -> null
    }

    private fun stringList(node: RawNode?): List<String> = when (node) {
        is RawNode.Sequence -> node.items.mapNotNull { scalar(it) }
        else -> emptyList()
    }
}

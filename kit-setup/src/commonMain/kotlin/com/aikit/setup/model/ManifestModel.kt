package com.aikit.setup.model

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.asMap
import com.aikit.setup.manifest.asStringList
import com.aikit.setup.manifest.boolOrNull
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.intOrNull
import com.aikit.setup.manifest.isNullish
import com.aikit.setup.manifest.stringOr
import com.aikit.setup.manifest.stringOrNull

/**
 * Pure functions that build the typed manifest view from the loader's
 * [RawNode]. Defensive everywhere — missing fields collapse to sensible empty
 * defaults so generation can proceed when validation passed but optional
 * blocks are absent.
 *
 * Keep this file small and rule-shaped: validation owns *what's required*,
 * this file owns *how to read it once it's there*.
 */
object ManifestModel {

    fun from(manifest: Manifest): TypedManifest {
        val root = manifest.raw
        return TypedManifest(
            manifestVersion = root.field("manifest_version").stringOr("0.0.0"),
            kitVersion = root.field("kit_version")?.stringOrNull(),
            languageCode = root.field("language_code").stringOr("en"),
            project = readProject(root.field("project")),
            stack = readStack(root.field("stack")),
            modules = root.field("modules")?.asList()?.map(::readModule).orEmpty(),
            targets = root.field("targets")?.asList()?.map(::readTarget).orEmpty(),
            renderTargets = root.field("render_targets")?.asStringList().orEmpty(),
            providers = root.field("providers")?.asList()?.map(::readProvider).orEmpty(),
            models = root.field("models")?.asList()?.map(::readModelEntry).orEmpty(),
            taskTypes = root.field("task_types")?.asList()?.map(::readTaskType).orEmpty(),
            promptDialects = root.field("prompt_dialects")?.asList()?.map(::readPackagePointer).orEmpty(),
            targetAdapters = root.field("target_adapters")?.asList()?.map(::readPackagePointer).orEmpty(),
            sharedPath = root.field("shared")?.field("path")?.stringOrNull(),
            agents = root.field("agents")?.asList()?.map(::readAgent).orEmpty(),
            tools = root.field("tools")?.asList()?.map(::readTool).orEmpty(),
            workflows = root.field("workflows")?.asList()?.map(::readWorkflow).orEmpty(),
            knowledge = readKnowledgeOrNull(root.field("knowledge")),
            policies = readPolicies(root.field("policies")),
            ui = readUiOrNull(root.field("ui")),
        )
    }

    private fun readUiOrNull(node: RawNode?): Ui? {
        if (node.isNullish()) return null
        // Treat empty mappings as "no UI" too — a profile that declared `ui: {}`
        // is the same as not declaring it.
        val mapping = (node as? RawNode.Mapping) ?: return null
        if (mapping.entries.isEmpty()) return null
        val colors = mapping.entries["colors"]?.asList()?.map(::readUiColor).orEmpty()
        return Ui(
            framework = mapping.entries["framework"]?.stringOrNull(),
            platforms = mapping.entries["platforms"]?.asStringList().orEmpty(),
            colors = colors,
        )
    }

    private fun readUiColor(node: RawNode): UiColor = UiColor(
        name = node.field("name").stringOr(""),
        hex = node.field("hex")?.stringOrNull(),
        purpose = node.field("purpose")?.stringOrNull(),
    )

    private fun readProject(node: RawNode?): Project = Project(
        name = node?.field("name").stringOr("Unnamed"),
        slug = node?.field("slug").stringOr("unnamed"),
        description = node?.field("description")?.stringOrNull(),
        repoUrl = node?.field("repo_url")?.stringOrNull(),
        owners = node?.field("owners")?.asStringList().orEmpty(),
    )

    private fun readStack(node: RawNode?): Stack = Stack(
        languages = node?.field("languages")?.asStringList().orEmpty(),
        frameworks = node?.field("frameworks")?.asStringList().orEmpty(),
        buildCommand = node?.field("build_command")?.stringOrNull(),
        compileCommand = node?.field("compile_command")?.stringOrNull(),
        lintCommand = node?.field("lint_command")?.stringOrNull(),
        testCommand = node?.field("test_command")?.stringOrNull(),
        formatCommand = node?.field("format_command")?.stringOrNull(),
        runCommand = node?.field("run_command")?.stringOrNull(),
    )

    private fun readModule(node: RawNode): Module = Module(
        name = node.field("name").stringOr(""),
        sourceRoot = node.field("source_root")?.stringOrNull(),
        testRoot = node.field("test_root")?.stringOrNull(),
        docsPath = node.field("docs_path")?.stringOrNull(),
        responsibility = node.field("responsibility")?.stringOrNull(),
    )

    private fun readTarget(node: RawNode): Target = Target(
        id = node.field("id").stringOr(""),
        nativeProvider = node.field("native_provider").stringOr("any"),
        canUseVia = node.field("can_use_via")?.asStringList().orEmpty(),
        adapter = node.field("adapter")?.stringOrNull(),
    )

    private fun readProvider(node: RawNode): Provider = Provider(
        id = node.field("id").stringOr(""),
        kind = node.field("kind").stringOr("custom"),
        baseUrl = node.field("base_url")?.stringOrNull(),
        apiKeyEnv = node.field("api_key_env")?.stringOrNull(),
        timeoutSeconds = node.field("timeout_seconds")?.intOrNull(),
        maxRetries = node.field("max_retries")?.intOrNull(),
        auth = parseProviderAuth(node.field("auth")?.stringOrNull()) ?: ProviderAuth.API_KEY,
    )

    private fun parseProviderAuth(s: String?): ProviderAuth? = when (s?.lowercase()) {
        null -> null
        "api_key", "api-key", "apikey" -> ProviderAuth.API_KEY
        "subscription", "runner", "runner_managed", "runner-managed" -> ProviderAuth.SUBSCRIPTION
        "none" -> ProviderAuth.NONE
        else -> ProviderAuth.UNKNOWN
    }

    private fun readModelEntry(node: RawNode): Model = Model(
        id = node.field("id").stringOr(""),
        provider = node.field("provider").stringOr(""),
        model = node.field("model").stringOr(""),
        family = node.field("family").stringOr("generic"),
        tier = parseTier(node.field("tier").stringOrNull()) ?: ModelTier.UNKNOWN,
        capabilities = node.field("capabilities").asStringList(),
        params = readParams(node.field("params")),
        costHint = parseCostHint(node.field("cost_hint").stringOrNull()) ?: CostHint.UNKNOWN,
        priority = node.field("priority")?.intOrNull() ?: 0,
        contextWindow = node.field("context_window")?.intOrNull(),
    )

    private fun readParams(node: RawNode?): Map<String, String> {
        if (node.isNullish()) return emptyMap()
        return node!!.asMap().mapValues { (_, v) -> v.stringOr("") }
    }

    private fun readTaskType(node: RawNode): TaskTypeRule = TaskTypeRule(
        id = node.field("id").stringOr(""),
        prefers = parseTier(node.field("prefers")?.stringOrNull()),
        minTier = parseTier(node.field("min_tier")?.stringOrNull()),
        needs = node.field("needs")?.asStringList().orEmpty(),
    )

    private fun readPackagePointer(node: RawNode): PackagePointer = PackagePointer(
        id = node.field("id").stringOr(""),
        path = node.field("path").stringOr(""),
    )

    private fun readAgent(node: RawNode): Agent {
        val ms = readModelSelection(node.field("model_selection"))
        val promptNode = node.field("prompt")
        val prompt = readPromptSpec(promptNode)
        val permsNode = node.field("permissions")
        val permissions = if (permsNode is RawNode.Mapping) {
            permsNode.entries.mapValues { (_, v) -> v.stringOr("") }
        } else emptyMap()
        val targetsOverride = node.field("targets_override")
            ?.takeUnless { it is RawNode.Null }
            ?.asStringList()

        return Agent(
            id = node.field("id").stringOr(""),
            role = node.field("role")?.stringOrNull(),
            description = node.field("description").stringOr(""),
            mode = node.field("mode")?.stringOrNull(),
            modelSelection = ms,
            prompt = prompt,
            tools = node.field("tools")?.asStringList().orEmpty(),
            permissions = permissions,
            targetsOverride = targetsOverride,
        )
    }

    /**
     * Reads a `prompt:` block. Two shapes are accepted:
     *  - short form: `prompt: { include: prompts/X.md }` (single body, dialect-wrapped)
     *  - long form: `prompt: { default: { include: ... }, anthropic: { include: ... } }`
     */
    private fun readPromptSpec(node: RawNode?): PromptSpec {
        if (node !is RawNode.Mapping) return PromptSpec(defaultPath = "", perFamily = emptyMap())
        // Short form check: a single `include` directly under prompt.
        val direct = node.entries["include"]?.stringOrNull()
        if (direct != null && node.entries.size == 1) {
            return PromptSpec(defaultPath = direct, perFamily = emptyMap())
        }
        var defaultPath = ""
        val perFamily = linkedMapOf<String, String>()
        for ((key, child) in node.entries) {
            val include = child.field("include")?.stringOrNull() ?: continue
            if (key == "default") defaultPath = include else perFamily[key] = include
        }
        return PromptSpec(defaultPath = defaultPath, perFamily = perFamily)
    }

    private fun readTool(node: RawNode): ToolEntry = ToolEntry(
        id = node.field("id").stringOr(""),
        kind = node.field("kind").stringOr(""),
        command = node.field("command")?.stringOrNull(),
        args = node.field("args")?.asStringList().orEmpty(),
        url = node.field("url")?.stringOrNull(),
        apiKeyEnv = node.field("api_key_env")?.stringOrNull(),
        enabled = node.field("enabled")?.boolOrNull() ?: true,
    )

    private fun readKnowledgeOrNull(node: RawNode?): Knowledge? {
        if (node.isNullish()) return null
        val constitution = readConstitutionOrNull(node!!.field("constitution"))
        val specs = readStoreOrNull(node.field("specs"))
        val session = readStoreOrNull(node.field("session"))
        return Knowledge(constitution = constitution, specs = specs, session = session)
    }

    private fun readConstitutionOrNull(node: RawNode?): Constitution? {
        if (node.isNullish()) return null
        // sections: list of single-key mappings — `- routing: { source: { include: knowledge/foo.md } }`
        val sections = node!!.field("sections")?.asList()?.mapNotNull { sec ->
            val map = (sec as? RawNode.Mapping)?.entries ?: return@mapNotNull null
            val (name, body) = map.entries.firstOrNull() ?: return@mapNotNull null
            val include = body.field("source")?.field("include")?.stringOrNull() ?: return@mapNotNull null
            ConstitutionSection(name = name, includePath = include)
        }.orEmpty()
        return Constitution(sections = sections, maxTokens = node.field("max_tokens")?.intOrNull())
    }

    private fun readStoreOrNull(node: RawNode?): KnowledgeStore? {
        if (node.isNullish()) return null
        val kind = parseStoreKind(node!!.field("kind")?.stringOrNull())
        val layout = node.field("layout")?.asMap()?.mapValues { (_, v) -> v.stringOr("") }.orEmpty()
        return KnowledgeStore(kind = kind, path = node.field("path")?.stringOrNull(), layout = layout)
    }

    private fun readPolicies(node: RawNode?): Policies = Policies(
        forbiddenPatterns = node?.field("forbidden_patterns")?.asStringList().orEmpty(),
        secretsDenyPatterns = node?.field("secrets_policy")?.field("deny_patterns")?.asStringList().orEmpty(),
        testStrategy = node?.field("test_strategy")?.stringOrNull(),
        sliceCaps = node?.field("slice_caps")?.asMap()?.mapValues { (_, v) -> v.stringOr("") }.orEmpty(),
        modelConstraints = readModelConstraints(node?.field("model_constraints")),
        optionalSkills = node?.field("optional_skills")?.asStringList().orEmpty(),
    )

    private fun readModelConstraints(node: RawNode?): Map<String, ModelConstraint> {
        if (node !is RawNode.Mapping) return emptyMap()
        val out = linkedMapOf<String, ModelConstraint>()
        for ((task, child) in node.entries) {
            out[task] = ModelConstraint(
                minTier = parseTier(child.field("min_tier").stringOrNull()),
                requireCapabilities = child.field("require_capabilities").asStringList(),
            )
        }
        return out
    }

    /**
     * Reads a `model_selection:` block recursively — `by_task` and `by_severity`
     * entries are themselves [ModelSelection]s (without further nesting), so the
     * same reader handles all three levels.
     */
    private fun readModelSelection(node: RawNode?): ModelSelection {
        if (node.isNullish()) return ModelSelection()
        return ModelSelection(
            needs = node.field("needs").asStringList(),
            prefers = parseTier(node.field("prefers").stringOrNull()),
            minTier = parseTier(node.field("min_tier").stringOrNull()),
            maxCost = parseCostHint(node.field("max_cost").stringOrNull()),
            pin = node.field("pin").stringOrNull(),
            byTask = readNestedSelections(node.field("by_task")),
            bySeverity = readNestedSelections(node.field("by_severity")),
        )
    }

    private fun readNestedSelections(node: RawNode?): Map<String, ModelSelection> {
        if (node !is RawNode.Mapping) return emptyMap()
        val out = linkedMapOf<String, ModelSelection>()
        for ((key, child) in node.entries) {
            out[key] = readModelSelection(child).copy(byTask = emptyMap(), bySeverity = emptyMap())
        }
        return out
    }

    private fun readWorkflow(node: RawNode): Workflow = Workflow(
        id = node.field("id").stringOr(""),
        description = node.field("description").stringOrNull(),
        trigger = node.field("trigger").stringOrNull(),
        steps = node.field("steps").asList().map { step ->
            WorkflowStep(
                agent = step.field("agent").stringOr(""),
                task = step.field("task").stringOrNull(),
                gate = step.field("gate").stringOrNull(),
                onFail = step.field("on_fail").stringOrNull(),
            )
        },
    )

    // ── enum coercion ────────────────────────────────────────────────────────

    private fun parseTier(s: String?): ModelTier? = when (s?.lowercase()) {
        null -> null
        "fast" -> ModelTier.FAST
        "balanced" -> ModelTier.BALANCED
        "reasoner" -> ModelTier.REASONER
        else -> ModelTier.UNKNOWN
    }

    private fun parseCostHint(s: String?): CostHint? = when (s?.lowercase()) {
        null -> null
        "cheap" -> CostHint.CHEAP
        "balanced" -> CostHint.BALANCED
        "premium" -> CostHint.PREMIUM
        else -> CostHint.UNKNOWN
    }

    private fun parseStoreKind(s: String?): StoreKind = when (s?.lowercase()) {
        null -> StoreKind.UNKNOWN
        "filesystem" -> StoreKind.FILESYSTEM
        "mcp" -> StoreKind.MCP
        "http" -> StoreKind.HTTP
        "composite" -> StoreKind.COMPOSITE
        else -> StoreKind.UNKNOWN
    }
}

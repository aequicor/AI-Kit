package com.aikit.setup.generation

import com.aikit.setup.io.FileWriter
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.model.Agent
import com.aikit.setup.model.ManifestModel
import com.aikit.setup.model.Model
import com.aikit.setup.model.Target
import com.aikit.setup.model.TypedManifest
import com.aikit.setup.model.primaryTaskFor
import com.aikit.setup.output.Json
import com.aikit.setup.templates.TemplateRegistry

/**
 * Manifest-driven kit generator. Walks each entry of [TypedManifest.renderTargets],
 * resolves the matching adapter and per-agent dialect, then writes every
 * artifact the adapter declares it can produce.
 *
 * Constructor dependencies follow the project's DIP convention:
 *  - [files] for writes only (no reads — that's [PackageLoader]'s job)
 *  - [templates] for embedded template lookup (single source of template state)
 *  - [packages] / [yamlVariablesProvider] are injectable for tests
 *
 * Generation overwrites unconditionally — same contract as the slot it
 * replaces, documented in `kit-setup/CLAUDE.md`.
 */
class DefaultKitGenerator(
    private val files: FileWriter,
    private val templates: TemplateRegistry,
    private val packages: PackageLoader,
    private val conditionals: ConditionalRenderer,
) : KitGenerator {

    /**
     * Scoped to a single `generate()` invocation — set at the top of the
     * method, read by [writeArtifact]. Resetting on every call keeps the
     * generator usable across runs without leaking deny-pattern state from
     * an earlier manifest into a later one.
     */
    private var outputGuard: OutputGuard = OutputGuard(emptyList())

    override fun generate(manifest: Manifest, targetRoot: String): GenerationResult {
        val typed = ManifestModel.from(manifest)
        val resolver = ModelResolver(typed)
        outputGuard = OutputGuard(typed.policies.secretsDenyPatterns)

        val written = mutableListOf<String>()
        val errors = mutableListOf<GenerationError>()

        for (targetId in typed.renderTargets) {
            val target = typed.targets.firstOrNull { it.id == targetId }
            if (target == null) {
                errors += GenerationError(
                    path = "",
                    code = "unknown_target",
                    message = "render_targets references undeclared target `$targetId`",
                )
                continue
            }
            val adapterPath = adapterPathFor(typed, target)
            if (adapterPath == null) {
                errors += GenerationError(
                    path = "",
                    code = "missing_adapter",
                    message = "no target_adapters[] entry for target `$targetId`",
                )
                continue
            }
            try {
                renderTarget(typed, target, adapterPath, resolver, targetRoot, written, errors)
            } catch (e: Throwable) {
                errors += GenerationError(
                    path = "",
                    code = "target_failed",
                    message = "target `$targetId`: ${e.message ?: e::class.simpleName}",
                )
            }
        }

        return GenerationResult(generatedFiles = written, errors = errors)
    }

    // ── per-target orchestration ─────────────────────────────────────────────

    private fun renderTarget(
        manifest: TypedManifest,
        target: Target,
        adapterPath: String,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val adapter = packages.loadAdapter(adapterPath)

        // Constitution / instruction file (CLAUDE.md, AGENTS.md, …).
        // The orchestrator agent (role: orchestrator, or the legacy `Main` id
        // with no role declared) is inlined here, not emitted as a subagent —
        // its prompt must live in the runner's main-loop system prompt so it
        // can drive multi-turn AWAIT gates that subagents structurally can't.
        renderInstructionFile(manifest, target, adapter, resolver, targetRoot, written, errors)

        // Agents (subagents only — orchestrator went into the instruction file).
        if (adapter.capabilities["subagents"] == true) {
            for (agent in manifest.agents) {
                if (agent.targetsOverride != null && target.id !in agent.targetsOverride) continue
                if (isOrchestrator(agent)) continue
                renderAgent(manifest, target, adapter, agent, resolver, targetRoot, written, errors)
            }
        }

        // Skills (one directory per skill in the templates tree).
        if (adapter.capabilities["skills_directory_format"] == true || adapter.artifactPaths["skill"] != null) {
            renderSkills(manifest, target, adapter, resolver, targetRoot, written, errors)
        }

        // Commands (slash commands).
        if (adapter.capabilities["slash_commands"] == true || adapter.artifactPaths["command"] != null) {
            renderCommands(manifest, target, adapter, resolver, targetRoot, written, errors)
        }

        // Rules (`rules/*.md`). Two shapes:
        //   - section-style (`CLAUDE.md#{id}`): folded into instruction file by renderInstructionFile.
        //   - file-style (`.cursor/rules/{id}.mdc`): one rule per file, dialect-wrapped.
        if (isFileStyleRule(adapter)) {
            renderRules(manifest, target, adapter, resolver, targetRoot, written, errors)
        }

        // User-prompts (`user-prompts/*.md`). Always file-style.
        if (adapter.artifactPaths["user_prompt"] != null) {
            renderUserPrompts(manifest, target, adapter, resolver, targetRoot, written, errors)
        }

        // Settings file.
        renderSettings(manifest, adapter, targetRoot, written, errors)

        // Project-scoped MCP file (e.g. Claude Code's `.mcp.json`).
        renderMcpFile(manifest, adapter, targetRoot, written, errors)
    }

    // ── instruction file (CLAUDE.md / AGENTS.md / …) ─────────────────────────

    private fun renderInstructionFile(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val out = adapter.instructionFile ?: return
        // Cursor maps `instruction_file` to a directory — `.cursor/rules` —
        // skip the single-file constitution write and rely on per-rule files
        // (renderRules emits the orchestrator body there too).
        if (out.endsWith("/")) return
        if (adapter.capabilities["scoped_rules"] == true) return
        val orchestrator = orchestratorAgentFor(manifest, target)
        val hasConstitution = !manifest.knowledge?.constitution?.sections.isNullOrEmpty() ||
            manifest.policies.forbiddenPatterns.isNotEmpty()
        if (!hasConstitution && orchestrator == null) {
            return
        }
        val baseVars = baseVariables(manifest)
        val defaultFamily = manifest.models.maxByOrNull { it.priority }?.family
            ?: manifest.promptDialects.firstOrNull()?.id
        val dialect = defaultFamily?.let { loadDialectFor(manifest, it) }
        val engine = PlaceholderEngine(templates, dialect?.packagePath ?: "", manifest.sharedPath)
        val ctxFamily = orchestratorFamily(manifest, target, resolver) ?: defaultFamily.orEmpty()
        val renderCtx = RenderContext(adapter.capabilities, target.id, ctxFamily)

        val sb = StringBuilder()
        sb.append("# ").append(manifest.project.name).append(" — kit constitution\n\n")
        for (section in manifest.knowledge?.constitution?.sections.orEmpty()) {
            val body = templates.read(section.includePath)
            if (body == null) {
                errors += GenerationError(
                    path = section.includePath,
                    code = "missing_constitution_section",
                    message = "constitution section `${section.name}` references missing template `${section.includePath}`",
                )
                continue
            }
            val conditioned = preprocessOrFail(body, renderCtx, section.includePath, errors) ?: continue
            sb.append("## ").append(section.name).append("\n\n")
            sb.append(engine.render(conditioned, baseVars).trimEnd()).append("\n\n")
        }
        if (manifest.policies.forbiddenPatterns.isNotEmpty()) {
            sb.append("## forbidden_patterns\n\n")
            for (p in manifest.policies.forbiddenPatterns) sb.append("- ").append(p).append('\n')
            sb.append('\n')
        }
        // Section-style rules — only fold in when the adapter declares them
        // as `<file>#{id}` shape (Claude Code / OpenCode / Aider / Qwen). For
        // file-style adapters (Cursor) rules go through renderRules instead.
        if (isSectionStyleRule(adapter, instructionFileTarget = out)) {
            for (rule in listRules(manifest.stack.languages)) {
                val body = templates.read(rule.bodyPath) ?: continue
                val conditioned = preprocessOrFail(body, renderCtx, rule.bodyPath, errors) ?: continue
                sb.append("## ").append(rule.id).append("\n\n")
                sb.append(engine.render(conditioned, baseVars).trimEnd()).append("\n\n")
            }
        }
        // Orchestrator agent — inline its rendered body (dialect-wrapped, no
        // frontmatter) so the runner's main loop carries the pipeline prompt
        // directly. Subagents on this target still get their own files.
        if (orchestrator != null) {
            val body = renderAgentBodyOrNull(manifest, target, adapter, orchestrator, resolver, errors)
            if (body != null) {
                sb.append("## ").append(orchestrator.id).append(" — orchestrator\n\n")
                sb.append(body.trimEnd()).append("\n\n")
            }
        }
        val content = sb.toString().trimEnd() + "\n"
        if (!enforceConstitutionBudget(content, manifest, out, errors)) return
        writeArtifact(targetRoot, out, content, written, errors)
    }

    /**
     * Refuses to write the constitution when the rendered size estimate
     * exceeds `knowledge.constitution.max_tokens`.
     *
     * Token estimation uses the standard `chars / 4` rule of thumb — fine
     * for English+code content typical of the constitution. Authors who
     * want a tighter bound should set max_tokens conservatively.
     */
    private fun enforceConstitutionBudget(
        content: String,
        manifest: TypedManifest,
        outPath: String,
        errors: MutableList<GenerationError>,
    ): Boolean {
        val cap = manifest.knowledge?.constitution?.maxTokens ?: return true
        val estimate = content.length / 4
        if (estimate <= cap) return true
        errors += GenerationError(
            path = outPath,
            code = "constitution_overflow",
            message = "Constitution rendered to ~$estimate tokens (chars/4); " +
                "knowledge.constitution.max_tokens=$cap. Refused to write.",
        )
        return false
    }

    // ── rules (file-style) ──────────────────────────────────────────────────

    /**
     * Emits one file per rule body for adapters whose `artifact_paths.rule`
     * is a file pattern (Cursor's `.cursor/rules/{id}.mdc`). Also projects
     * each constitution section into its own rule file so Cursor users get
     * the constitution split across `alwaysApply: true` `.mdc` files — the
     * idiomatic way that runner consumes always-loaded context.
     */
    private fun renderRules(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outTemplate = adapter.artifactPaths["rule"] ?: return
        val defaultFamily = manifest.models.maxByOrNull { it.priority }?.family
            ?: manifest.promptDialects.firstOrNull()?.id
            ?: return
        val dialect = loadDialectFor(manifest, defaultFamily) ?: return
        val wrapperPath = dialect.wrappers["rule"]?.let { "${dialect.packagePath}/$it" }
        val wrapper = wrapperPath?.let { templates.read(it) }

        val baseVars = baseVariables(manifest)
        val engine = PlaceholderEngine(templates, dialect.packagePath, manifest.sharedPath)
        val ctxFamily = orchestratorFamily(manifest, target, resolver) ?: defaultFamily
        val renderCtx = RenderContext(adapter.capabilities, target.id, ctxFamily)

        // 1. constitution sections → per-section rule files (alwaysApply: true).
        for (section in manifest.knowledge?.constitution?.sections.orEmpty()) {
            val body = templates.read(section.includePath) ?: continue
            val conditioned = preprocessOrFail(body, renderCtx, section.includePath, errors) ?: continue
            emitRuleFile(adapter, outTemplate, dialect, wrapper, engine, baseVars, section.name, conditioned, alwaysApply = true, targetRoot, written, errors)
        }

        // 2. explicit `rules/*.md` bodies — filtered by manifest.stack.languages.
        for (rule in listRules(manifest.stack.languages)) {
            val body = templates.read(rule.bodyPath) ?: continue
            val conditioned = preprocessOrFail(body, renderCtx, rule.bodyPath, errors) ?: continue
            emitRuleFile(adapter, outTemplate, dialect, wrapper, engine, baseVars, rule.id, conditioned, alwaysApply = false, targetRoot, written, errors)
        }

        // 3. orchestrator agent body → alwaysApply rule file. File-style
        // adapters (Cursor) have no main-loop system prompt, so the
        // orchestrator pipeline is delivered as an always-loaded rule. Use
        // the agent wrapper output verbatim — placeholders and dialect
        // wrapping already resolved by renderWrappedAgent.
        val orchestrator = orchestratorAgentFor(manifest, target)
        if (orchestrator != null) {
            val rendered = renderAgentBodyOrNull(manifest, target, adapter, orchestrator, resolver, errors)
            if (rendered != null) {
                emitRuleFile(adapter, outTemplate, dialect, wrapper = null, engine, baseVars, orchestrator.id, rendered, alwaysApply = true, targetRoot, written, errors)
            }
        }
    }

    private fun emitRuleFile(
        adapter: Adapter,
        outTemplate: String,
        dialect: Dialect,
        wrapper: String?,
        engine: PlaceholderEngine,
        baseVars: Map<String, String>,
        id: String,
        body: String,
        alwaysApply: Boolean,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val description = describeBody(body)
        val resolvedBody = engine.render(body, baseVars).trimEnd()
        val variables = baseVars + mapOf(
            "RULE_ID" to id,
            "RULE_DESC" to description,
            "RULE_GLOBS_JSON" to "[]",
            "RULE_ALWAYS_APPLY" to alwaysApply.toString(),
            "BODY" to resolvedBody,
        )
        val rendered = if (wrapper != null) engine.render(wrapper, variables) else resolvedBody
        val fmTemplatePath = adapter.artifactFrontmatter["rule"]?.let { "${adapter.packagePath}/$it" }
        val frontmatter = fmTemplatePath?.let { templates.read(it) }?.let {
            Frontmatter.render(it, variables)
        } ?: ""
        val outPath = outTemplate.replace("{id}", id)
        writeArtifact(targetRoot, outPath, frontmatter + rendered.trimEnd() + "\n", written, errors)
    }

    // ── user-prompts ────────────────────────────────────────────────────────

    private fun renderUserPrompts(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outTemplate = adapter.artifactPaths["user_prompt"] ?: return
        val files = templates.list("user-prompts/").filter { it.endsWith(".md") }
        if (files.isEmpty()) return

        val defaultFamily = manifest.models.maxByOrNull { it.priority }?.family
            ?: manifest.promptDialects.firstOrNull()?.id
            ?: return
        val dialect = loadDialectFor(manifest, defaultFamily) ?: return
        val wrapperPath = dialect.wrappers["user_prompt"]?.let { "${dialect.packagePath}/$it" }
        val wrapper = wrapperPath?.let { templates.read(it) }

        val baseVars = baseVariables(manifest)
        val engine = PlaceholderEngine(templates, dialect.packagePath, manifest.sharedPath)
        val ctxFamily = orchestratorFamily(manifest, target, resolver) ?: defaultFamily
        val renderCtx = RenderContext(adapter.capabilities, target.id, ctxFamily)

        for (path in files) {
            val id = path.removePrefix("user-prompts/").removeSuffix(".md")
            val rawBody = templates.read(path) ?: continue
            val body = preprocessOrFail(rawBody, renderCtx, path, errors) ?: continue
            val description = describeBody(body)
            val resolvedBody = engine.render(body, baseVars).trimEnd()
            val variables = baseVars + mapOf(
                "PROMPT_ID" to id,
                "PROMPT_DESC" to description,
                // Alias for cursor's user-prompt frontmatter template, which
                // names this slot {{USER_PROMPT_TITLE}}.
                "USER_PROMPT_TITLE" to description,
                "BODY" to resolvedBody,
            )
            val rendered = if (wrapper != null) engine.render(wrapper, variables) else resolvedBody
            val fmTemplatePath = adapter.artifactFrontmatter["user_prompt"]?.let { "${adapter.packagePath}/$it" }
            val frontmatter = fmTemplatePath?.let { templates.read(it) }?.let {
                Frontmatter.render(it, variables)
            } ?: ""
            val outPath = outTemplate.replace("{id}", id)
            writeArtifact(targetRoot, outPath, frontmatter + rendered.trimEnd() + "\n", written, errors)
        }
    }

    private data class RuleEntry(val id: String, val bodyPath: String)

    /**
     * Lists rules applicable to this manifest, filtered by language.
     *
     * Convention:
     *  - `rules/<id>.md`              — language-agnostic (always emitted).
     *  - `rules/shared/<id>.md`       — language-agnostic (always emitted; explicit form).
     *  - `rules/<language>/<id>.md`   — only emitted when `<language>` is in the manifest's
     *                                   `stack.languages` (matched case-insensitively).
     *
     * Match is case-insensitive on the language segment so `Kotlin` and `kotlin` both work.
     */
    private fun listRules(languages: List<String>): List<RuleEntry> {
        val normalized = languages.map { it.lowercase() }.toSet()
        return templates.list("rules/")
            .filter { it.endsWith(".md") }
            .mapNotNull { path ->
                val rest = path.removePrefix("rules/")
                val firstSlash = rest.indexOf('/')
                if (firstSlash < 0) {
                    // rules/<id>.md — language-agnostic.
                    return@mapNotNull RuleEntry(id = rest.removeSuffix(".md"), bodyPath = path)
                }
                val segment = rest.substring(0, firstSlash).lowercase()
                val tail = rest.substring(firstSlash + 1).removeSuffix(".md")
                if (segment == "shared" || segment in normalized) {
                    RuleEntry(id = tail, bodyPath = path)
                } else {
                    null
                }
            }
    }

    /** True iff the adapter writes rules as sections inside [instructionFileTarget]. */
    private fun isSectionStyleRule(adapter: Adapter, instructionFileTarget: String): Boolean {
        val ruleSlot = adapter.artifactPaths["rule"] ?: return false
        val hashIdx = ruleSlot.indexOf('#')
        if (hashIdx < 0) return false
        return ruleSlot.substring(0, hashIdx) == instructionFileTarget
    }

    /** True iff the adapter writes rules as separate files (not sections). */
    private fun isFileStyleRule(adapter: Adapter): Boolean {
        val ruleSlot = adapter.artifactPaths["rule"] ?: return false
        return !ruleSlot.contains('#')
    }

    // ── agents ───────────────────────────────────────────────────────────────

    private fun renderAgent(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        agent: Agent,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outTemplate = adapter.artifactPaths["agent"] ?: return
        val rendered = renderWrappedAgent(manifest, target, adapter, agent, resolver, errors) ?: return

        val fmTemplatePath = adapter.artifactFrontmatter["agent"]
            ?.let { "${adapter.packagePath}/$it" }
        val frontmatter = fmTemplatePath?.let { templates.read(it) }?.let {
            Frontmatter.render(it, rendered.variables)
        } ?: ""

        val outPath = outTemplate.replace("{id}", agent.id)
        val content = frontmatter + rendered.body.trimEnd() + "\n"
        writeArtifact(targetRoot, outPath, content, written, errors)
    }

    /**
     * Renders an agent prompt (dialect-wrapped, placeholders expanded) without
     * writing anything. Returns the body and the variable map (the latter so
     * a caller that needs frontmatter can reuse the resolved values).
     *
     * Used both by [renderAgent] (subagents → per-runner files) and by
     * [renderInstructionFile] / [renderRules] (orchestrator → inlined into
     * the runner's main-loop prompt). All error reporting is via [errors];
     * a `null` return means a fatal lookup failed and the caller should skip.
     */
    private data class RenderedAgent(val body: String, val variables: Map<String, String>)

    private fun renderWrappedAgent(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        agent: Agent,
        resolver: ModelResolver,
        errors: MutableList<GenerationError>,
    ): RenderedAgent? {
        val primaryTask = primaryTaskFor(manifest.workflows, agent.id)
        val resolved = resolver.resolve(target, agent, taskType = primaryTask)
        if (resolved == null) {
            errors += GenerationError(
                path = "/agents/${agent.id}",
                code = "no_model_matched",
                message = "no model satisfied agents[${agent.id}] for target `${target.id}`",
            )
            return null
        }
        val dialect = loadDialectFor(manifest, resolved.family)
        if (dialect == null) {
            errors += GenerationError(
                path = "/agents/${agent.id}",
                code = "missing_dialect",
                message = "no prompt_dialects[] entry for family `${resolved.family}`",
            )
            return null
        }

        val bodyPath = agent.prompt.perFamily[resolved.family] ?: agent.prompt.defaultPath
        val rawBody = templates.read(bodyPath) ?: run {
            errors += GenerationError(
                path = bodyPath,
                code = "missing_prompt_body",
                message = "prompt body `$bodyPath` not found in templates",
            )
            return null
        }
        val renderCtx = RenderContext(
            capabilities = adapter.capabilities,
            targetId = target.id,
            family = resolved.family,
        )
        val body = preprocessOrFail(rawBody, renderCtx, bodyPath, errors) ?: return null

        val wrapper = templates.read("${dialect.packagePath}/${dialect.wrappers["agent"]}") ?: run {
            errors += GenerationError(
                path = dialect.packagePath,
                code = "missing_agent_wrapper",
                message = "dialect `${dialect.id}` has no agent wrapper",
            )
            return null
        }

        val baseVars = baseVariables(manifest) + mapOf(
            "AGENT_ID" to agent.id,
            // Wrappers like "You are X — {AGENT_DESC}." append their own
            // sentence terminator. Trim trailing punctuation here so author-
            // provided descriptions ending in `.` don't yield "….. .".
            "AGENT_DESC" to agent.description.trimEnd().trimEnd('.', '!', '?').trim(),
            "TOOLS_LIST" to agent.tools.joinToString("\n") { "- $it" },
            "TOOLS_LIST_CSV" to agent.tools.joinToString(", "),
            "RESOLVED_MODEL" to resolved.model,
        )
        val engine = PlaceholderEngine(templates, dialect.packagePath, manifest.sharedPath)
        // Expand variables and snippets inside the body first; otherwise tokens
        // like {{TEST_COMMAND}} written by prompt authors would survive the
        // single-pass wrapper substitution unresolved.
        val resolvedBody = engine.render(body, baseVars).trimEnd()
        val variables = baseVars + mapOf("BODY" to resolvedBody)
        val rendered = engine.render(wrapper, variables)
        return RenderedAgent(body = rendered, variables = variables)
    }

    /**
     * Thin wrapper used when only the rendered body is needed (no frontmatter,
     * no file write) — instruction-file inlining for the orchestrator.
     */
    private fun renderAgentBodyOrNull(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        agent: Agent,
        resolver: ModelResolver,
        errors: MutableList<GenerationError>,
    ): String? = renderWrappedAgent(manifest, target, adapter, agent, resolver, errors)?.body

    /**
     * True for the agent that drives the runner's main loop. Two signals:
     *  - explicit `role: orchestrator` in the manifest (preferred);
     *  - legacy heuristic: an agent with `id == "Main"` and no `role` declared.
     *
     * Use [orchestratorAgentFor] to fetch the (at most one) orchestrator
     * filtered by `targets_override`; the unicity invariant is enforced by
     * [com.aikit.setup.validation.rules.OrchestratorUnicityRule].
     */
    private fun isOrchestrator(agent: Agent): Boolean {
        if (agent.role?.equals("orchestrator", ignoreCase = true) == true) return true
        if (agent.role == null && agent.id == "Main") return true
        return false
    }

    private fun orchestratorAgentFor(manifest: TypedManifest, target: Target): Agent? =
        manifest.agents.firstOrNull { agent ->
            isOrchestrator(agent) && (agent.targetsOverride == null || target.id in agent.targetsOverride)
        }

    // ── skills ───────────────────────────────────────────────────────────────

    private fun renderSkills(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outTemplate = adapter.artifactPaths["skill"] ?: return
        // Skills live as `skills/<id>/SKILL.md` in the template tree.
        val ids = templates.list("skills/")
            .mapNotNull { key ->
                val rest = key.removePrefix("skills/")
                val slash = rest.indexOf('/')
                if (slash < 0) null else rest.substring(0, slash)
            }.distinct()
        if (ids.isEmpty()) return
        val enabledOptionalSkills = manifest.policies.optionalSkills.toSet()

        // Pick a default dialect: use the family of the highest-priority
        // anthropic-provider model when available, else first declared dialect.
        // A more nuanced binding (skill → specific agent) can layer in later.
        val defaultFamily = manifest.models.maxByOrNull { it.priority }?.family
            ?: manifest.promptDialects.firstOrNull()?.id
            ?: return
        val dialect = loadDialectFor(manifest, defaultFamily) ?: return
        val wrapperPath = dialect.wrappers["skill"]
            ?.let { "${dialect.packagePath}/$it" }
            ?: return
        val wrapper = templates.read(wrapperPath) ?: return

        val baseVars = baseVariables(manifest)
        val engine = PlaceholderEngine(templates, dialect.packagePath, manifest.sharedPath)
        val ctxFamily = orchestratorFamily(manifest, target, resolver) ?: defaultFamily
        val renderCtx = RenderContext(adapter.capabilities, target.id, ctxFamily)

        for (id in ids) {
            val bodyPath = "skills/$id/SKILL.md"
            val rawBody = templates.read(bodyPath) ?: continue
            // Optional skills must be explicitly opted-in via policies.optional_skills.
            // The marker lives in the raw body so the check happens before any
            // dialect wrapping or placeholder expansion alters the file.
            if (isOptionalSkill(rawBody) && id !in enabledOptionalSkills) continue
            val body = preprocessOrFail(rawBody, renderCtx, bodyPath, errors) ?: continue
            val sections = parseSkillSections(body)
            val procedureText = sections.procedure.ifEmpty { body }
            val resolvedBody = engine.render(procedureText, baseVars).trimEnd()
            val variables = baseVars + mapOf(
                "SKILL_ID" to id,
                "SKILL_DESCRIPTION" to sections.description,
                "SKILL_TRIGGERS" to engine.render(sections.triggers, baseVars),
                "SKILL_OUTPUT_FORMAT" to engine.render(sections.outputFormat, baseVars),
                "BODY" to resolvedBody,
            )
            val rendered = engine.render(wrapper, variables)
            val fmTemplatePath = adapter.artifactFrontmatter["skill"]
                ?.let { "${adapter.packagePath}/$it" }
            val frontmatter = fmTemplatePath?.let { templates.read(it) }?.let {
                Frontmatter.render(it, variables)
            } ?: ""
            val outPath = outTemplate.replace("{id}", id)
            val content = frontmatter + rendered.trimEnd() + "\n"
            writeArtifact(targetRoot, outPath, content, written, errors)
        }
    }

    // ── commands ─────────────────────────────────────────────────────────────

    private fun renderCommands(
        manifest: TypedManifest,
        target: Target,
        adapter: Adapter,
        resolver: ModelResolver,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outTemplate = adapter.artifactPaths["command"] ?: return
        val files = templates.list("commands/")
            .filter { it.endsWith(".md") }
        if (files.isEmpty()) return

        val defaultFamily = manifest.models.maxByOrNull { it.priority }?.family
            ?: manifest.promptDialects.firstOrNull()?.id
            ?: return
        val dialect = loadDialectFor(manifest, defaultFamily) ?: return
        val wrapperPath = dialect.wrappers["command"]
            ?.let { "${dialect.packagePath}/$it" }
            ?: return
        val wrapper = templates.read(wrapperPath) ?: return

        val baseVars = baseVariables(manifest)
        val engine = PlaceholderEngine(templates, dialect.packagePath, manifest.sharedPath)
        val ctxFamily = orchestratorFamily(manifest, target, resolver) ?: defaultFamily
        val renderCtx = RenderContext(adapter.capabilities, target.id, ctxFamily)

        for (path in files) {
            val id = path.removePrefix("commands/").removeSuffix(".md")
            val rawBody = templates.read(path) ?: continue
            val body = preprocessOrFail(rawBody, renderCtx, path, errors) ?: continue
            val description = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val resolvedBody = engine.render(body, baseVars).trimEnd()
            val variables = baseVars + mapOf(
                "COMMAND_NAME" to id,
                "COMMAND_DESCRIPTION" to description,
                "COMMAND_ARGS" to "",
                "TOOLS_LIST" to "",
                "TOOLS_LIST_CSV" to "",
                "BODY" to resolvedBody,
            )
            val rendered = engine.render(wrapper, variables)
            val fmTemplatePath = adapter.artifactFrontmatter["command"]
                ?.let { "${adapter.packagePath}/$it" }
            val frontmatter = fmTemplatePath?.let { templates.read(it) }?.let {
                Frontmatter.render(it, variables)
            } ?: ""
            val outPath = outTemplate.replace("{id}", id)
            val content = frontmatter + rendered.trimEnd() + "\n"
            writeArtifact(targetRoot, outPath, content, written, errors)
        }
    }

    // ── settings file ────────────────────────────────────────────────────────

    private fun renderSettings(
        manifest: TypedManifest,
        adapter: Adapter,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outPath = adapter.settingsFile ?: return
        val templatePath = adapter.settingsTemplate?.let { "${adapter.packagePath}/$it" } ?: return
        val template = templates.read(templatePath) ?: run {
            errors += GenerationError(
                path = templatePath,
                code = "missing_settings_template",
                message = "settings template `$templatePath` not found",
            )
            return
        }
        val mcp = mcpServersJson(manifest)
        val provider = manifest.providers.firstOrNull()
        val permissions = PermissionResolver(manifest)
        val variables = baseVariables(manifest) + mapOf(
            "MCP_SERVERS_JSON" to mcp,
            "PERMISSIONS_ALLOW_JSON" to permissions.claudeCodeAllowJson(),
            "PERMISSIONS_DENY_JSON" to permissions.claudeCodeDenyJson(),
            "OPENCODE_PERMISSION_JSON" to permissions.opencodePermissionJson(),
            "PROVIDER_ID" to (provider?.id ?: ""),
            "PROVIDER_BASE_URL" to (provider?.baseUrl ?: ""),
            "PROVIDER_API_KEY_ENV" to (provider?.apiKeyEnv ?: ""),
            "RESOLVED_MODEL" to (manifest.models.firstOrNull()?.model ?: ""),
        )
        // Settings templates are JSON/YAML, not markdown, so we just substitute
        // variables — no snippet expansion. PlaceholderEngine handles both, but
        // running snippet resolution here is wasted work and might match stray
        // `{{...}}` that's part of the JSON value space.
        var rendered = template
        for ((k, v) in variables) rendered = rendered.replace("{{$k}}", v)
        writeArtifact(targetRoot, outPath, rendered.trimEnd() + "\n", written, errors)
    }

    // ── project-scoped MCP file (.mcp.json) ─────────────────────────────────
    //
    // Claude Code reads project MCP servers from `.mcp.json` at the repo
    // root, NOT from `.claude/settings.json → mcpServers` (the latter is
    // silently ignored by current Claude Code releases). Adapters whose
    // runner separates the two declare `mcp_file` + `mcp_template` in
    // `adapter.yaml`; OpenCode and Qwen Code keep their MCP block inline in
    // the main settings file, so `mcpFile` stays null there and this step
    // is a no-op for them.
    //
    // Emission is skipped when no `tools[]` entry has an enabled `mcp-*`
    // kind — there is no value in dropping an empty `.mcp.json` next to
    // projects that don't use MCP.
    private fun renderMcpFile(
        manifest: TypedManifest,
        adapter: Adapter,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val outPath = adapter.mcpFile ?: return
        val templatePath = adapter.mcpTemplate?.let { "${adapter.packagePath}/$it" } ?: return
        val hasMcpTool = manifest.tools.any { it.enabled && it.kind.startsWith("mcp-") }
        if (!hasMcpTool) return
        val template = templates.read(templatePath) ?: run {
            errors += GenerationError(
                path = templatePath,
                code = "missing_mcp_template",
                message = "MCP file template `$templatePath` not found",
            )
            return
        }
        val mcp = mcpServersJson(manifest)
        var rendered = template.replace("{{MCP_SERVERS_JSON}}", mcp)
        // Project file path is project-root relative, not config-dir relative —
        // .mcp.json sits next to package.json / build.gradle, not under .claude/.
        writeArtifact(targetRoot, outPath, rendered.trimEnd() + "\n", written, errors)
    }

    /**
     * Builds the MCP server JSON object from `tools[]` entries whose kind
     * starts with `mcp-`. Different kinds project to different shapes —
     * stdio gets `command` + `args`, http gets `url` (+ optional `headers`
     * placeholder for the api-key env). Empty object when no MCP tools exist.
     */
    private fun mcpServersJson(manifest: TypedManifest): String {
        val obj = linkedMapOf<String, Any?>()
        for (tool in manifest.tools) {
            if (!tool.kind.startsWith("mcp-")) continue
            if (!tool.enabled) continue
            val entry = linkedMapOf<String, Any?>()
            when (tool.kind) {
                "mcp-stdio" -> {
                    if (tool.command != null) entry["command"] = tool.command
                    if (tool.args.isNotEmpty()) entry["args"] = tool.args
                }
                "mcp-http", "mcp-sse" -> {
                    if (tool.url != null) entry["url"] = tool.url
                }
            }
            obj[tool.id] = entry
        }
        return Json.encode(obj)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun adapterPathFor(manifest: TypedManifest, target: Target): String? {
        val adapterId = target.adapter ?: target.id
        return manifest.targetAdapters.firstOrNull { it.id == adapterId }?.path
    }

    private fun loadDialectFor(manifest: TypedManifest, family: String): Dialect? {
        val pointer = manifest.promptDialects.firstOrNull { it.id == family } ?: return null
        return packages.loadDialect(pointer.path)
    }

    private fun baseVariables(manifest: TypedManifest): Map<String, String> {
        val out = linkedMapOf(
            "PROJECT_NAME" to manifest.project.name,
            "PROJECT_SLUG" to manifest.project.slug,
            "STACK_SUMMARY" to stackSummary(manifest),
            "LANGUAGE_CODE" to manifest.languageCode,
            "LANGUAGE_NAME" to languageNameFor(manifest.languageCode),
            "FORBIDDEN_PATTERNS" to manifest.policies.forbiddenPatterns.joinToString("\n") { "- $it" },
            "BUILD_COMMAND" to (manifest.stack.buildCommand ?: ""),
            "TEST_COMMAND" to (manifest.stack.testCommand ?: ""),
            "LINT_COMMAND" to (manifest.stack.lintCommand ?: ""),
            "FORMAT_COMMAND" to (manifest.stack.formatCommand ?: ""),
            "RUN_COMMAND" to (manifest.stack.runCommand ?: ""),
            "COMPILE_COMMAND" to (manifest.stack.compileCommand ?: ""),
        )
        // Expose policies.slice_caps as SLICE_CAPS_<KEY> so prompts can quote
        // numeric ceilings without the typed model needing a fixed schema.
        for ((k, v) in manifest.policies.sliceCaps) {
            out["SLICE_CAPS_${k.uppercase()}"] = v
        }
        // KnowledgeOS opt-in: the id `knowledge-os` is well-known. Declaring it
        // in tools[] (kind: mcp-*, enabled) flips KNOWLEDGE_OS_ENABLED on, which
        // memory snippets gate on. KNOWLEDGE_OS_DISABLED is its inverse so
        // snippets can express the filesystem-fallback branch (the placeholder
        // engine has no `{{#else}}`).
        val osOn = isKnowledgeOsEnabled(manifest)
        out["KNOWLEDGE_OS_ENABLED"] = if (osOn) "1" else ""
        out["KNOWLEDGE_OS_DISABLED"] = if (osOn) "" else "1"
        // Markdown-bullet inventory of every enabled tool/MCP from manifest.tools[].
        // Commands and agent prompts use this to decide which capabilities apply
        // to a task before they start research — without it the templates have to
        // hard-code a tool table that drifts from reality.
        out["AVAILABLE_TOOLS"] = manifest.tools
            .filter { it.enabled }
            .joinToString("\n") { "- ${it.id} (${it.kind})" }
        return out
    }

    /**
     * Pulls a one-line description out of a body file: the first non-blank line,
     * with any leading `#` markdown header markers and quotes stripped so the
     * result is safe to drop into YAML frontmatter `description: "..."` slots.
     */
    private fun describeBody(body: String): String {
        val raw = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return raw.trimStart('#', ' ').replace("\"", "'")
    }

    private fun stackSummary(manifest: TypedManifest): String {
        val parts = mutableListOf<String>()
        if (manifest.stack.languages.isNotEmpty()) parts += manifest.stack.languages.joinToString(", ")
        if (manifest.stack.frameworks.isNotEmpty()) parts += manifest.stack.frameworks.joinToString(", ")
        return parts.joinToString(" / ")
    }

    /**
     * Map an ISO 639-1 (or BCP-47) code to an English name agents will reliably
     * understand. Falls back to the raw code for unknown values so the prompt
     * still carries *some* signal.
     *
     * Why English names: model instruction-following on language is much
     * stronger when the prompt says "Russian" than when it says "ru". The ISO
     * code alone forces the model to first translate "ru" → "Russian" — that
     * implicit step is where drift to English happens.
     */
    private fun languageNameFor(code: String): String {
        val base = code.trim().lowercase().substringBefore('-').substringBefore('_')
        return when (base) {
            "en" -> "English"
            "ru" -> "Russian"
            "uk" -> "Ukrainian"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "nl" -> "Dutch"
            "pl" -> "Polish"
            "tr" -> "Turkish"
            "cs" -> "Czech"
            "sv" -> "Swedish"
            "no", "nb" -> "Norwegian"
            "da" -> "Danish"
            "fi" -> "Finnish"
            "el" -> "Greek"
            "he" -> "Hebrew"
            "ar" -> "Arabic"
            "fa" -> "Persian"
            "hi" -> "Hindi"
            "id" -> "Indonesian"
            "vi" -> "Vietnamese"
            "th" -> "Thai"
            "zh" -> "Chinese"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            else -> code.trim().ifEmpty { "English" }
        }
    }

    private fun writeArtifact(
        targetRoot: String,
        outPath: String,
        content: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        // Hard refuse to write under any `vault/` segment. The vault is user
        // data (KnowledgeOS docs, hand-authored specs); the generator must
        // never overwrite it. A path like `vault/foo` or `<adapter>/vault/x`
        // is a misconfiguration in templates or a regression — surface it as
        // a stable, public-contract error rather than silently destroying
        // someone's notes.
        if (refersToVault(outPath)) {
            errors += GenerationError(
                path = outPath,
                code = "vault_write_refused",
                message = "Refused to write `$outPath`: paths under any " +
                    "`vault/` segment are user data and never overwritten by " +
                    "the generator. This is a regression — fix the adapter " +
                    "template or generator emitter.",
            )
            return
        }
        val matches = outputGuard.scan(content)
        if (matches.isNotEmpty()) {
            errors += GenerationError(
                path = outPath,
                code = "secret_pattern_match",
                message = "Refused to write `$outPath`: rendered content matches " +
                    "secrets_policy.deny_patterns ${matches.joinToString(prefix = "[", postfix = "]")}.",
            )
            return
        }
        val absPath = joinPath(targetRoot, outPath)
        try {
            val parent = absPath.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) files.mkdirs(parent)
            files.writeFile(absPath, content)
            written += outPath
        } catch (e: Throwable) {
            errors += GenerationError(
                path = outPath,
                code = "write_failed",
                message = e.message ?: e::class.simpleName ?: "unknown error",
            )
        }
    }

    private fun joinPath(root: String, rel: String): String {
        val r = root.trimEnd('/')
        val p = rel.trimStart('/')
        return if (r.isEmpty() || r == ".") p else "$r/$p"
    }

    /**
     * Runs [body] through the [ConditionalRenderer] using [ctx]. Returns the
     * preprocessed body, or `null` after recording a stable-coded
     * [GenerationError] on failure — callers `?: continue` past the bad file
     * so a single bad template doesn't kill the whole target. The renderer's
     * exception carries the stable error code; we re-tag with [path] so the
     * agent can pinpoint the offending file.
     */
    private fun preprocessOrFail(
        body: String,
        ctx: RenderContext,
        path: String,
        errors: MutableList<GenerationError>,
    ): String? = try {
        conditionals.render(body, ctx)
    } catch (e: ConditionalRenderException) {
        errors += GenerationError(
            path = path,
            code = e.code,
            message = e.message ?: e::class.simpleName.orEmpty(),
        )
        null
    }

    /**
     * Resolves the model family bound to the orchestrator agent for [target]
     * — the family used by templates that ship with the runner's main-loop
     * prompt (commands, file-style rules, skills, user-prompts). Returns
     * `null` when no orchestrator is declared; callers fall back to the
     * highest-priority model's family in that case.
     */
    private fun orchestratorFamily(
        manifest: TypedManifest,
        target: Target,
        resolver: ModelResolver,
    ): String? {
        val orchestrator = orchestratorAgentFor(manifest, target) ?: return null
        val primary = primaryTaskFor(manifest.workflows, orchestrator.id)
        return resolver.resolve(target, orchestrator, taskType = primary)?.family
    }

}

/**
 * True when [outPath] refers to a location under any `vault/` directory
 * segment. Match is case-sensitive — the project's vault convention is
 * lowercase. Splits on both `/` and `\` so a Windows-style path that slips
 * in from a buggy template is also caught. Module-level for direct unit
 * testing without exercising the full generator pipeline.
 */
internal fun refersToVault(outPath: String): Boolean =
    outPath.split('/', '\\').any { it == VAULT_DIR_NAME }

/**
 * Well-known tools[].id for KnowledgeOS (https://github.com/aequicor/KnowledgeOS).
 * Declaring an entry with this id, kind starting with `mcp-`, and `enabled: true`
 * turns on the `KNOWLEDGE_OS_ENABLED` placeholder flag — memory snippets in agent
 * / command / skill prompts gate their MCP-based branch on it. Documented in
 * `templates/kit-manifect.yaml` (search for KNOWLEDGE_OS_ENABLED).
 */
internal const val KNOWLEDGE_OS_TOOL_ID = "knowledge-os"

/**
 * Reserved directory name treated as user data. Any output path containing
 * this segment is refused at write time with the stable error code
 * `vault_write_refused` — see `DefaultKitGenerator.writeArtifact`.
 */
internal const val VAULT_DIR_NAME = "vault"

/**
 * Returns true when the manifest enables the KnowledgeOS MCP backend. Public to
 * the module so the predicate can be unit-tested without exercising the full
 * generator pipeline.
 */
internal fun isKnowledgeOsEnabled(manifest: TypedManifest): Boolean =
    manifest.tools.any {
        it.id == KNOWLEDGE_OS_TOOL_ID && it.enabled && it.kind.startsWith("mcp-")
    }

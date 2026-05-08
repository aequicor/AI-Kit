package com.aikit.setup.generation

import com.aikit.setup.embedded.EmbeddedTemplates
import com.aikit.setup.io.FileWriter
import com.aikit.setup.manifest.DefaultYamlParser
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.TypedModule
import com.aikit.setup.manifest.YamlParser
import com.aikit.setup.profile.HostMetadata
import com.aikit.setup.profile.Profile
import com.aikit.setup.profile.ProfileAxis
import com.aikit.setup.profile.ProfileLoader
import com.aikit.setup.render.TemplateRenderer

/**
 * Default [KitGenerator] implementation. Drives the manifest-driven
 * rendering pipeline:
 *
 *  1. Resolve the chosen profiles from the embedded templates so each host
 *     in `manifest.hosts` can be paired with its [HostMetadata].
 *  2. Walk `kit/_index.txt` and ask [TemplateClassifier] which host(s)
 *     each entry renders for.
 *  3. Render each entry via [TemplateRenderer] (INCLUDE → VAR substitution)
 *     using the per-host [SubstitutionContextBuilder] map.
 *  4. Write the rendered file to `<target>/<host_relative_path>` via the
 *     injected [FileWriter].
 *  5. Render the per-module nested instruction file once per module per
 *     host. Append the local-only `.planning/CURRENT.md` line to
 *     `<target>/.gitignore`.
 *
 * The generator never reads the filesystem — every source lives in
 * [EmbeddedTemplates], so the output is reproducible from the binary alone.
 */
class DefaultKitGenerator(
    private val files: FileWriter,
    private val parser: YamlParser = DefaultYamlParser(),
    private val renderer: TemplateRenderer = TemplateRenderer(),
    private val classifier: TemplateClassifier = TemplateClassifier(),
    private val substitutionBuilder: SubstitutionContextBuilder = SubstitutionContextBuilder(),
    private val profileLoader: ProfileLoader = ProfileLoader(parser),
    private val kitRepo: String = DEFAULT_KIT_REPO,
) : KitGenerator {

    override fun generate(manifest: Manifest, targetRoot: String): GenerationResult {
        val typed = manifest.typed
        val written = mutableListOf<String>()
        val errors = mutableListOf<GenerationError>()

        val profilesResult = profileLoader.loadAll(typed.stack.profiles)
        for (e in profilesResult.errors) {
            errors += GenerationError(path = "profiles/${e.profileName}.yaml", code = e.code, message = e.message)
        }
        if (errors.isNotEmpty()) return GenerationResult(written, errors)

        val hostContexts = buildHostContexts(typed, profilesResult.profiles)
        if (hostContexts.isEmpty()) {
            errors += GenerationError(
                path = "/hosts",
                code = "no_host_profile_resolved",
                message = "No host profiles were resolved for hosts=${typed.hosts}.",
            )
            return GenerationResult(written, errors)
        }

        val indexContent = EmbeddedTemplates.files["kit/_index.txt"]
            ?: return GenerationResult(
                written,
                listOf(
                    GenerationError(
                        path = "kit/_index.txt",
                        code = "kit_index_missing",
                        message = "kit/_index.txt is not embedded in the binary.",
                    ),
                ),
            )

        val activeHosts = hostContexts.map { it.hostName }
        val hostByName = hostContexts.associateBy { it.hostName }

        for (entryRaw in indexContent.lines()) {
            val entry = entryRaw.trim()
            if (entry.isEmpty()) continue
            val actions = classifier.classify(entry, activeHosts, typed.vaultPath)
            for (action in actions) {
                val host = hostByName[action.host] ?: continue
                if (skipDesignerForNullModel(action, typed, host.hostName)) continue
                val source = EmbeddedTemplates.files[action.sourcePath]
                if (source == null) {
                    errors += GenerationError(
                        path = action.sourcePath,
                        code = "embedded_source_missing",
                        message = "Source file '${action.sourcePath}' is not embedded.",
                    )
                    continue
                }
                if (action.verbatim) {
                    writeFile(targetRoot, action.targetRelPath, source, written, errors)
                    continue
                }
                val rendered = try {
                    renderer.render(source, host.variables)
                } catch (t: Throwable) {
                    errors += GenerationError(
                        path = action.sourcePath,
                        code = "render_failed",
                        message = t.message ?: t::class.simpleName ?: "render error",
                    )
                    continue
                }
                if (rendered.unresolved.isNotEmpty()) {
                    errors += GenerationError(
                        path = action.targetRelPath,
                        code = "unresolved_placeholders",
                        message = "Unresolved placeholders: ${rendered.unresolved.joinToString(", ")}",
                    )
                    continue
                }
                if (action.optional && rendered.text.isBlank()) continue
                writeFile(targetRoot, action.targetRelPath, rendered.text, written, errors)
            }
        }

        renderModuleInstructionFiles(typed.modules, hostContexts, targetRoot, written, errors)
        ensureVaultScaffold(typed.modules, typed.vaultPath, targetRoot, written, errors)

        return GenerationResult(written, errors)
    }

    private fun buildHostContexts(
        typed: com.aikit.setup.manifest.TypedManifest,
        profiles: List<Profile>,
    ): List<HostContext> {
        val hostProfiles = profiles.filter { it.axis == ProfileAxis.HOST && it.host != null }
            .associateBy { it.name }
        return typed.hosts.mapNotNull { hostName ->
            val profile = hostProfiles[hostName] ?: return@mapNotNull null
            val metadata = profile.host ?: return@mapNotNull null
            val vars = substitutionBuilder.build(typed, hostName, metadata, kitRepo)
            HostContext(hostName, metadata, vars)
        }
    }

    private fun skipDesignerForNullModel(action: TemplateAction, typed: com.aikit.setup.manifest.TypedManifest, host: String): Boolean {
        if (!action.targetRelPath.endsWith("/Designer.md") && !action.targetRelPath.endsWith("Designer.body.md")) return false
        return when (host) {
            "opencode" -> typed.models?.designer == null
            "claude-code" -> typed.claudeCode?.models?.designer == null
            else -> false
        }
    }

    private fun renderModuleInstructionFiles(
        modules: List<TypedModule>,
        hosts: List<HostContext>,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val source = EmbeddedTemplates.files["kit/nested/MODULE.body.md.template"] ?: return
        for (m in modules) {
            for (host in hosts) {
                val moduleVars = host.variables + buildModuleVariables(m, host.variables)
                val rendered = try {
                    renderer.render(source, moduleVars)
                } catch (t: Throwable) {
                    errors += GenerationError(
                        path = "kit/nested/MODULE.body.md.template",
                        code = "render_failed",
                        message = "module=${m.name} host=${host.hostName}: ${t.message ?: ""}",
                    )
                    continue
                }
                if (rendered.unresolved.isNotEmpty()) {
                    errors += GenerationError(
                        path = "${m.sourceRoot}${host.metadata.instructionFile}",
                        code = "unresolved_placeholders",
                        message = "Unresolved placeholders in module file: ${rendered.unresolved.joinToString(", ")}",
                    )
                    continue
                }
                writeFile(
                    targetRoot,
                    "${m.sourceRoot.trimEnd('/')}/${host.metadata.instructionFile}",
                    rendered.text,
                    written,
                    errors,
                )
            }
        }
    }

    private fun buildModuleVariables(m: TypedModule, host: Map<String, String>): Map<String, String> {
        val buildCmd = host["BUILD_COMMAND"] ?: ""
        val testCmd = host["TEST_COMMAND_TEMPLATE"] ?: ""
        val compileCmd = host["COMPILE_COMMAND"] ?: ""
        val gradleLine = if (m.gradleModule != null) "**Gradle module:** `${m.gradleModule}`"
        else "**Gradle:** (not a Gradle project)"
        val docsPath = m.docsPath.trimEnd('/') + "/"
        return mapOf(
            "MODULE_NAME" to m.name,
            "MODULE_SOURCE_ROOT" to m.sourceRoot,
            "MODULE_TEST_ROOT" to m.testRoot,
            "MODULE_RESPONSIBILITY" to m.responsibility,
            "MODULE_GRADLE_LINE" to gradleLine,
            "MODULE_BUILD_TABLE" to renderModuleBuildTable(m, buildCmd, testCmd, compileCmd),
            "MODULE_CONVENTIONS" to (
                m.conventions.takeIf { it.isNotBlank() }
                    ?: "(use project-default conventions from root ${host["HOST_INSTRUCTION_FILE"]})"
                ),
            "MODULE_DEPENDENCIES" to "- `${m.name}`: ${m.moduleDependencies.takeIf { it.isNotBlank() } ?: "(none specified)"}",
            "MODULE_DOCS_PATH" to docsPath,
        )
    }

    private fun renderModuleBuildTable(m: TypedModule, build: String, test: String, compile: String): String {
        return if (m.gradleModule != null) {
            "| `$build ${m.gradleModule}:build` | Build this module |\n" +
                "| `$build ${m.gradleModule}:test` | Run tests |\n" +
                "| `$build ${m.gradleModule}:compileKotlin` | Quick compile |"
        } else {
            "| `$build` | Build project |\n| `$test` | Run tests |\n| `$compile` | Quick compile |"
        }
    }

    private fun ensureVaultScaffold(
        modules: List<TypedModule>,
        vaultPath: String,
        targetRoot: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val vaultRoot = vaultPath.trimEnd('/')
        val genres = listOf(
            "concepts/{m}/requirements",
            "concepts/{m}/plans",
            "reference/{m}/spec",
            "reference/{m}/test-cases",
            "how-to/{m}/plans",
            "tutorials/{m}/documentation",
            "guidelines/{m}/reports",
        )
        for (m in modules) {
            for (genre in genres) {
                val path = "$vaultRoot/${genre.replace("{m}", m.name)}/.gitkeep"
                writeFile(targetRoot, path, "", written, errors)
            }
        }
        writeFile(targetRoot, "$vaultRoot/guidelines/libs/.gitkeep", "", written, errors)
    }

    private fun writeFile(
        targetRoot: String,
        relPath: String,
        content: String,
        written: MutableList<String>,
        errors: MutableList<GenerationError>,
    ) {
        val absPath = joinPath(targetRoot, relPath)
        val parent = absPath.substringBeforeLast('/', missingDelimiterValue = "")
        try {
            if (parent.isNotEmpty()) files.mkdirs(parent)
            files.writeFile(absPath, content)
            written += relPath
        } catch (t: Throwable) {
            errors += GenerationError(
                path = relPath,
                code = "write_failed",
                message = t.message ?: t::class.simpleName ?: "write error",
            )
        }
    }

    private fun joinPath(root: String, rel: String): String {
        val r = root.trimEnd('/')
        val p = rel.trimStart('/')
        return if (r.isEmpty()) p else "$r/$p"
    }

    private companion object {
        const val DEFAULT_KIT_REPO: String = "aequicor/AI-Kit"
    }
}

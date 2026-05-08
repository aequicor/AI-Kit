package com.aikit.setup.cli

import com.aikit.setup.model.AiProvider
import com.aikit.setup.model.Language
import com.aikit.setup.model.ProjectConfig

class CliParser {

    fun parse(args: Array<String>): ParseResult {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return ParseResult.ShowHelp
        }

        val flags = parseFlags(args)

        val projectPath = flags["--path"] ?: flags["-p"] ?: "."
        val projectName = flags["--name"] ?: flags["-n"] ?: inferProjectName(projectPath)
        val languageArg = flags["--lang"] ?: flags["-l"] ?: "kotlin"
        val framework = flags["--framework"] ?: flags["-f"]
        val providerArg = flags["--provider"] ?: "both"
        val model = flags["--model"] ?: flags["-m"]
        val enablePlanning = !flags.containsKey("--no-planning")
        val enableAgents = !flags.containsKey("--no-agents")

        val language = Language.entries.firstOrNull {
            it.name.equals(languageArg, ignoreCase = true) ||
                it.displayName.equals(languageArg, ignoreCase = true)
        } ?: return ParseResult.Error("Unknown language: $languageArg. Valid: ${Language.entries.joinToString { it.name.lowercase() }}")

        val provider = AiProvider.entries.firstOrNull {
            it.name.equals(providerArg, ignoreCase = true) ||
                it.displayName.equals(providerArg, ignoreCase = true)
        } ?: return ParseResult.Error("Unknown provider: $providerArg. Valid: claude, opencode, both")

        val resolvedModel = model ?: when (provider) {
            AiProvider.CLAUDE -> "claude-sonnet-4-6"
            AiProvider.OPENCODE -> "claude-sonnet-4-6"
            AiProvider.BOTH -> "claude-sonnet-4-6"
        }

        return ParseResult.Config(
            ProjectConfig(
                projectName = projectName,
                projectPath = projectPath,
                language = language,
                framework = framework,
                aiProvider = provider,
                model = resolvedModel,
                enablePlanning = enablePlanning,
                enableAgents = enableAgents,
            )
        )
    }

    private fun parseFlags(args: Array<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-")) {
                if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                    result[arg] = args[i + 1]
                    i += 2
                } else {
                    result[arg] = "true"
                    i++
                }
            } else {
                i++
            }
        }
        return result
    }

    private fun inferProjectName(path: String): String {
        val normalized = path.replace('\\', '/')
        return normalized.trimEnd('/').substringAfterLast('/').ifEmpty { "my-project" }
    }
}

sealed class ParseResult {
    data object ShowHelp : ParseResult()
    data class Error(val message: String) : ParseResult()
    data class Config(val config: ProjectConfig) : ParseResult()
}

fun printHelp() {
    println(
        """
        kit-setup — AI agent configuration generator

        USAGE:
            kit-setup [OPTIONS]

        OPTIONS:
            -n, --name <name>          Project name (default: inferred from path)
            -p, --path <path>          Target project path (default: current directory)
            -l, --lang <language>      Project language: kotlin, java, python, typescript, go, rust, other
                                       (default: kotlin)
            -f, --framework <name>     Framework name, e.g. spring, ktor, react (optional)
                --provider <provider>  AI provider: claude, opencode, both (default: both)
            -m, --model <model>        AI model ID (default: claude-sonnet-4-6)
                --no-planning          Skip generating .planning/ structure
                --no-agents            Skip generating agent definitions
            -h, --help                 Show this help message

        EXAMPLES:
            kit-setup --name my-app --path ./my-app --lang kotlin --provider both
            kit-setup -p . -l typescript -f react --provider claude
            kit-setup --path /projects/backend --lang java --framework spring
        """.trimIndent()
    )
}

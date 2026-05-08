package com.aikit.setup.generator

import com.aikit.setup.model.AiProvider
import com.aikit.setup.model.DEFAULT_AGENTS
import com.aikit.setup.model.ProjectConfig

class ProjectGenerator(
    private val fs: FileSystem,
    private val claudeGen: ClaudeCodeGenerator = ClaudeCodeGenerator(),
    private val openCodeGen: OpenCodeGenerator = OpenCodeGenerator(),
) {

    fun generate(config: ProjectConfig): GenerationResult {
        val generatedFiles = mutableListOf<String>()
        val errors = mutableListOf<String>()

        fun write(relativePath: String, content: String) {
            val fullPath = joinPath(config.projectPath, relativePath)
            try {
                fs.mkdirs(parentOf(fullPath))
                fs.writeFile(fullPath, content)
                generatedFiles.add(relativePath)
            } catch (e: Exception) {
                errors.add("Failed to write $relativePath: ${e.message}")
            }
        }

        val generateClaude = config.aiProvider == AiProvider.CLAUDE || config.aiProvider == AiProvider.BOTH
        val generateOpenCode = config.aiProvider == AiProvider.OPENCODE || config.aiProvider == AiProvider.BOTH

        if (generateClaude) {
            write("CLAUDE.md", claudeGen.generateClaudeMd(config))
            write(".claude/settings.json", claudeGen.generateSettings(config))

            if (config.enableAgents) {
                for (agent in DEFAULT_AGENTS) {
                    write(".claude/agents/${agent.name}.md", claudeGen.generateAgentFile(agent))
                }
            }
        }

        if (generateOpenCode) {
            write("opencode.json", openCodeGen.generateConfig(config))

            if (config.enableAgents) {
                for (agent in DEFAULT_AGENTS) {
                    write(".opencode/agents/${agent.name}.md", openCodeGen.generateAgentFile(agent))
                }
            }
        }

        if (config.enablePlanning) {
            write(".planning/CURRENT.md", planningCurrentMd(config))
            write(".planning/MORNING_REPORT.md", planningMorningReportMd())
            write(".planning/tasks/.gitkeep", "")
        }

        return GenerationResult(
            projectPath = config.projectPath,
            generatedFiles = generatedFiles,
            errors = errors,
        )
    }

    private fun planningCurrentMd(config: ProjectConfig) = buildString {
        appendLine("# Current Session — ${config.projectName}")
        appendLine()
        appendLine("## Active Context")
        appendLine()
        appendLine("_Update this file when switching contexts or starting a new session._")
        appendLine()
        appendLine("## Current Focus")
        appendLine()
        appendLine("- [ ] Initial project setup")
        appendLine()
        appendLine("## Recent Decisions")
        appendLine()
        appendLine("_None yet._")
    }

    private fun planningMorningReportMd() = buildString {
        appendLine("# Morning Report")
        appendLine()
        appendLine("_This file is updated by the orchestrator agent at the start of each session._")
        appendLine()
        appendLine("## Yesterday")
        appendLine()
        appendLine("_No prior sessions._")
        appendLine()
        appendLine("## Today's Plan")
        appendLine()
        appendLine("_Pending._")
    }

    private fun joinPath(base: String, relative: String): String {
        val b = base.trimEnd('/', '\\')
        val r = relative.trimStart('/', '\\')
        return "$b/$r"
    }

    private fun parentOf(path: String): String {
        val normalized = path.replace('\\', '/')
        return normalized.substringBeforeLast('/', "")
    }
}

data class GenerationResult(
    val projectPath: String,
    val generatedFiles: List<String>,
    val errors: List<String>,
) {
    val success: Boolean get() = errors.isEmpty()
}

interface FileSystem {
    fun mkdirs(path: String)
    fun writeFile(path: String, content: String)
    fun exists(path: String): Boolean
}

package com.aikit.setup.generator

import com.aikit.setup.model.AgentDefinition
import com.aikit.setup.model.ProjectConfig

class ClaudeCodeGenerator {

    fun generateSettings(config: ProjectConfig): String = buildString {
        appendLine("""{
  "permissions": {
    "allow": [
      "Bash(git:*)",
      "Bash(ls:*)",
      "Bash(find:*)",
      "Bash(cat:*)",
      "Bash(grep:*)"
    ],
    "deny": []
  },
  "model": "${config.model}",
  "hooks": {}
}""")
    }

    fun generateClaudeMd(config: ProjectConfig): String = buildString {
        appendLine("# ${config.projectName}")
        appendLine()
        appendLine("## Project Overview")
        appendLine()
        appendLine("**Language:** ${config.language.displayName}")
        if (config.framework != null) {
            appendLine("**Framework:** ${config.framework}")
        }
        appendLine()
        appendLine("## AI Agent Configuration")
        appendLine()
        appendLine("This project uses Claude Code with a multi-agent workflow.")
        if (config.enableAgents) {
            appendLine("Agents are defined in `.claude/agents/`.")
        }
        if (config.enablePlanning) {
            appendLine()
            appendLine("## Planning System")
            appendLine()
            appendLine("- Session context: `.planning/CURRENT.md`")
            appendLine("- Task files: `.planning/tasks/<slug>.md`")
            appendLine("- Morning report: `.planning/MORNING_REPORT.md`")
        }
        appendLine()
        appendLine("## Development Guidelines")
        appendLine()
        appendLine("- Follow existing code conventions")
        appendLine("- Write tests for new functionality")
        appendLine("- Keep commits small and focused")
        appendLine("- Update `.planning/CURRENT.md` when switching contexts")
    }

    fun generateAgentFile(agent: AgentDefinition): String = buildString {
        appendLine("---")
        appendLine("name: ${agent.name}")
        appendLine("description: ${agent.description}")
        appendLine("model: ${agent.model}")
        appendLine("---")
        appendLine()
        appendLine(agent.systemPrompt)
    }
}

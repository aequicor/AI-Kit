package com.aikit.setup.generator

import com.aikit.setup.model.AgentDefinition
import com.aikit.setup.model.ProjectConfig

class OpenCodeGenerator {

    fun generateConfig(config: ProjectConfig): String = buildString {
        val agentsBlock = if (config.enableAgents) {
            """  "agents": {
    "enabled": true,
    "path": ".opencode/agents"
  },"""
        } else {
            """  "agents": {
    "enabled": false
  },"""
        }

        appendLine(
            """{
  "model": "${config.model}",
  "provider": "anthropic",
  $agentsBlock
  "autoshare": false,
  "theme": "opencode"
}"""
        )
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

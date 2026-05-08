package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Each agent must declare a `default` prompt body (short or long form). The
 * generator falls back to `default` when no per-family override matches the
 * resolved model's family — without it, rendering would produce an empty file.
 */
class AgentPromptPresentRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        manifest.raw.field("agents").asList().forEachIndexed { idx, agent ->
            val prompt = agent.field("prompt") as? RawNode.Mapping
            if (prompt == null) {
                errors += ValidationError(
                    path = "/agents/$idx/prompt",
                    code = "missing_agent_prompt",
                    message = "agents[$idx].prompt is missing.",
                )
                return@forEachIndexed
            }
            val direct = prompt.entries["include"]?.stringOrNull()
            val default = prompt.entries["default"]?.field("include")?.stringOrNull()
            if (direct == null && default == null) {
                errors += ValidationError(
                    path = "/agents/$idx/prompt",
                    code = "missing_default_prompt_body",
                    message = "agents[$idx].prompt must declare a default `include`.",
                    hint = "Use `prompt: { include: prompts/X.md }` or `prompt.default: { include: ... }`.",
                )
            }
        }
        return errors
    }
}

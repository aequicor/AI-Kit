package com.aikit.setup.validation.rules

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.asList
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * The orchestrator role is inlined into the runner's main-loop prompt
 * (CLAUDE.md / AGENTS.md / CONVENTIONS.md / an alwaysApply rule for Cursor).
 * Only one agent can occupy that slot per target — declaring two with
 * `role: orchestrator` is ambiguous, so reject it upfront with a path that
 * points at each duplicate entry.
 *
 * The legacy heuristic (`id == "Main"` and no explicit role) is treated as
 * orchestrator for back-compat, but coexisting with another agent that
 * declares `role: orchestrator` is still ambiguous and flagged the same way.
 */
class OrchestratorUnicityRule : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val agents = manifest.raw.field("agents").asList()
        val orchestratorIndices = agents.mapIndexedNotNull { idx, agent ->
            val role = agent.field("role")?.stringOrNull()?.lowercase()
            val id = agent.field("id")?.stringOrNull()
            val isOrchestrator = role == "orchestrator" || (role == null && id == "Main")
            if (isOrchestrator) idx else null
        }
        if (orchestratorIndices.size <= 1) return emptyList()
        return orchestratorIndices.map { idx ->
            ValidationError(
                path = "/agents/$idx/role",
                code = "multiple_orchestrators",
                message = "More than one orchestrator agent declared. Only one agent " +
                    "per manifest may carry `role: orchestrator` (or the legacy `id: Main` " +
                    "shorthand) — its body is inlined into the runner's main-loop prompt.",
                hint = "Keep one orchestrator; demote the others by removing `role: orchestrator` " +
                    "(they become subagents) or by renaming away from `Main`.",
            )
        }
    }
}

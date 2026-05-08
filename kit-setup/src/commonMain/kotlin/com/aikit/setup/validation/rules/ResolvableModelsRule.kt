package com.aikit.setup.validation.rules

import com.aikit.setup.generation.ModelResolver
import com.aikit.setup.generation.PackageLoader
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.model.ManifestModel
import com.aikit.setup.model.primaryTaskFor
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Simulates the generator's per-agent model resolution at verify time.
 *
 * Without this rule, a manifest where an agent's `needs[]` cannot be
 * satisfied by any provider the target accepts only fails halfway through
 * `generate` — partial output, confusing exit code. Running the resolver
 * here surfaces those data anomalies before any file is written.
 *
 * Per (agent × render-target × inferred primary task):
 *  - skip targets the agent's `targetsOverride` excludes
 *  - skip targets whose adapter doesn't support subagents (no agent file
 *    would be written, so resolution doesn't happen there)
 *  - infer the primary task from `workflows[]` (same logic as the generator)
 *  - call [ModelResolver.resolve] and report `unresolvable_model` on null
 *
 * The rule also reports `missing_adapter` when an adapter package can't be
 * loaded for a render target — that's a configuration error the structural
 * rules don't catch.
 */
class ResolvableModelsRule(
    private val packages: PackageLoader,
) : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val typed = ManifestModel.from(manifest)
        val resolver = ModelResolver(typed)
        val errors = mutableListOf<ValidationError>()

        for (renderTargetId in typed.renderTargets) {
            val target = typed.targets.firstOrNull { it.id == renderTargetId } ?: continue
            val adapterId = target.adapter ?: target.id
            val adapterPointer = typed.targetAdapters.firstOrNull { it.id == adapterId }
            if (adapterPointer == null) {
                errors += ValidationError(
                    path = "/target_adapters",
                    code = "missing_adapter",
                    message = "Target `${target.id}` has no `target_adapters[]` entry " +
                        "for adapter id `$adapterId`.",
                )
                continue
            }
            val adapter = try {
                packages.loadAdapter(adapterPointer.path)
            } catch (e: Throwable) {
                errors += ValidationError(
                    path = "/target_adapters/${adapterId}",
                    code = "missing_adapter",
                    message = e.message ?: "Adapter package failed to load.",
                )
                continue
            }
            val supportsSubagents = adapter.capabilities["subagents"] == true
            if (!supportsSubagents) continue

            for ((agentIdx, agent) in typed.agents.withIndex()) {
                if (agent.targetsOverride != null && target.id !in agent.targetsOverride) continue
                val task = primaryTaskFor(typed.workflows, agent.id)
                val resolved = resolver.resolveStrict(target, agent, taskType = task)
                if (resolved == null) {
                    errors += ValidationError(
                        path = "/agents/$agentIdx",
                        code = "unresolvable_model",
                        message = "Agent `${agent.id}` cannot resolve a model for target " +
                            "`${target.id}`" + (task?.let { " (task `$it`)" }.orEmpty()) +
                            ". No declared model satisfies the agent's `needs[]` " +
                            "given the target's allowed providers.",
                        hint = "Loosen `model_selection.needs`, raise `max_cost`, " +
                            "or declare a model the target's providers can use.",
                    )
                }
            }
        }
        return errors
    }
}

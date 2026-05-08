package com.aikit.setup.generation

import com.aikit.setup.model.Agent
import com.aikit.setup.model.CostHint
import com.aikit.setup.model.Model
import com.aikit.setup.model.ModelConstraint
import com.aikit.setup.model.ModelSelection
import com.aikit.setup.model.ModelTier
import com.aikit.setup.model.Target
import com.aikit.setup.model.TaskTypeRule
import com.aikit.setup.model.TypedManifest

/**
 * Picks one [Model] per (agent, target, optional task, optional severity).
 *
 * The algorithm follows the resolver order documented in
 * `templates/kit-manifect.yaml` next to the `agents:` block:
 *
 *  1. **Build the effective selection.** Layers, later overriding earlier:
 *     - `task_types[task]` defaults from the manifest (`prefers`, `needs`, `min_tier`)
 *     - `agent.modelSelection` (the agent's own request)
 *     - `agent.modelSelection.byTask[task]` (per-task override)
 *     - `agent.modelSelection.bySeverity[severity]` (per-severity override)
 *  2. **Honor pin** at the most specific layer (severity > task > base).
 *  3. **Apply kit-wide constraints.** `policies.model_constraints[task]`
 *     can only tighten — `min_tier` is raised to the floor, required
 *     capabilities are added to `needs`. Agents cannot soften these.
 *  4. **Filter** `models[]` by the target's allowed providers and the
 *     effective `needs` ⊆ `model.capabilities`.
 *  5. **Floor / ceiling**: drop anything below `min_tier` or above `max_cost`.
 *  6. **Sort**: tier matches `prefers` first, then cheaper cost, then higher
 *     priority. Falls back to "any allowed model" if the strict pass empties out.
 */
class ModelResolver(
    private val manifest: TypedManifest,
) {

    /**
     * Resolves a model for the given [agent] under [target]. When the agent's
     * primary work in this kit is task-specific (e.g. BugFixer → debug),
     * pass [taskType] so `by_task` / `task_types` layers apply. [severity]
     * is honored only by agents that declare `by_severity`.
     */
    fun resolve(
        target: Target,
        agent: Agent,
        taskType: String? = null,
        severity: String? = null,
    ): Model? = pick(target, agent, taskType, severity, allowFallback = true)

    /**
     * Like [resolve] but returns null if no model strictly satisfies the
     * effective selection. Verify-time rules use this to flag configurations
     * where the fallback would silently downgrade — a generator-time success
     * shouldn't hide a verify-time data anomaly.
     */
    fun resolveStrict(
        target: Target,
        agent: Agent,
        taskType: String? = null,
        severity: String? = null,
    ): Model? = pick(target, agent, taskType, severity, allowFallback = false)

    private fun pick(
        target: Target,
        agent: Agent,
        taskType: String?,
        severity: String?,
        allowFallback: Boolean,
    ): Model? {
        val effective = effectiveSelection(agent.modelSelection, taskType, severity)

        // Pin precedence: severity > task > base. effectiveSelection already
        // resolves it because nested layers override earlier ones.
        if (effective.pin != null) {
            return manifest.models.firstOrNull { it.id == effective.pin }
        }

        val allowedProviders = providersFor(target)
        val pool = manifest.models.filter {
            allowedProviders.isEmpty() || it.provider in allowedProviders
        }
        val needSet = effective.needs.toSet()
        val strict = pool
            .filter { needSet.all { cap -> cap in it.capabilities } }
            .filter { passesMinTier(it.tier, effective.minTier) }
            .filter { passesMaxCost(it.costHint, effective.maxCost) }
        val candidates = if (strict.isEmpty() && allowFallback) pool else strict
        return candidates.sortedWith(rankBy(effective.prefers)).firstOrNull()
    }

    /**
     * Composes the agent's request with `task_types[task]` defaults, the
     * agent's `by_task[task]` / `by_severity[severity]` overrides, and the
     * kit-wide `policies.model_constraints[task]` floor.
     */
    internal fun effectiveSelection(
        base: ModelSelection,
        taskType: String?,
        severity: String?,
    ): ModelSelection {
        var merged = if (taskType != null) {
            mergeSelection(taskTypeDefaults(taskType), base)
        } else {
            base
        }
        if (taskType != null) {
            base.byTask[taskType]?.let { merged = mergeSelection(merged, it) }
        }
        if (severity != null) {
            base.bySeverity[severity]?.let { merged = mergeSelection(merged, it) }
        }
        if (taskType != null) {
            manifest.policies.modelConstraints[taskType]?.let {
                merged = applyConstraint(merged, it)
            }
        }
        return merged
    }

    /** Right-wins on every nullable field; needs lists are unioned. */
    private fun mergeSelection(left: ModelSelection, right: ModelSelection): ModelSelection =
        ModelSelection(
            needs = (left.needs + right.needs).distinct(),
            prefers = right.prefers ?: left.prefers,
            minTier = right.minTier ?: left.minTier,
            maxCost = right.maxCost ?: left.maxCost,
            pin = right.pin ?: left.pin,
            // by_task / by_severity collapse — they only matter at the top
            // layer and effectiveSelection has already consumed them.
            byTask = emptyMap(),
            bySeverity = emptyMap(),
        )

    private fun applyConstraint(s: ModelSelection, c: ModelConstraint): ModelSelection {
        val tightenedFloor = when {
            c.minTier == null -> s.minTier
            s.minTier == null -> c.minTier
            tierRank(c.minTier) > tierRank(s.minTier) -> c.minTier
            else -> s.minTier
        }
        val tightenedNeeds = (s.needs + c.requireCapabilities).distinct()
        return s.copy(minTier = tightenedFloor, needs = tightenedNeeds)
    }

    private fun taskTypeDefaults(taskType: String): ModelSelection {
        val rule: TaskTypeRule = manifest.taskTypes.firstOrNull { it.id == taskType }
            ?: return ModelSelection()
        return ModelSelection(
            needs = rule.needs,
            prefers = rule.prefers,
            minTier = rule.minTier,
        )
    }

    private fun providersFor(target: Target): Set<String> {
        val out = mutableSetOf<String>()
        if (target.nativeProvider != "any") out += target.nativeProvider
        out += target.canUseVia
        if (target.nativeProvider == "any") {
            out += manifest.providers.map { it.id }
        }
        return out
    }

    private fun passesMinTier(actual: ModelTier, min: ModelTier?): Boolean {
        if (min == null) return true
        return tierRank(actual) >= tierRank(min)
    }

    private fun passesMaxCost(actual: CostHint, max: CostHint?): Boolean {
        if (max == null) return true
        return costRank(actual) <= costRank(max)
    }

    private fun rankBy(prefers: ModelTier?): Comparator<Model> = Comparator { a, b ->
        if (prefers != null) {
            val aMatch = a.tier == prefers
            val bMatch = b.tier == prefers
            if (aMatch != bMatch) return@Comparator if (aMatch) -1 else 1
        }
        val costCmp = costRank(a.costHint).compareTo(costRank(b.costHint))
        if (costCmp != 0) return@Comparator costCmp
        b.priority.compareTo(a.priority)
    }

    private fun tierRank(t: ModelTier): Int = when (t) {
        ModelTier.FAST -> 1
        ModelTier.BALANCED -> 2
        ModelTier.REASONER -> 3
        ModelTier.UNKNOWN -> 0
    }

    private fun costRank(c: CostHint): Int = when (c) {
        CostHint.CHEAP -> 1
        CostHint.BALANCED -> 2
        CostHint.PREMIUM -> 3
        CostHint.UNKNOWN -> 2
    }
}

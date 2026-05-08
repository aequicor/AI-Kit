package com.aikit.setup.model

/**
 * A pipeline declared under `workflows:`. The generator uses workflow steps to
 * infer each agent's "primary task" — the first step that mentions the agent
 * provides the [WorkflowStep.task] used when picking the model for that
 * agent's generated frontmatter.
 */
data class Workflow(
    val id: String,
    val description: String?,
    val trigger: String?,
    val steps: List<WorkflowStep>,
)

/** One step in a [Workflow]. */
data class WorkflowStep(
    val agent: String,
    val task: String?,
    val gate: String?,
    val onFail: String?,
)

/**
 * Returns the task the agent is "primarily" associated with — the task in the
 * first workflow step that mentions [agentId]. Both the generator (when
 * picking a model for the per-agent file) and verify-time resolution
 * simulation rely on this answer, so the lookup lives next to the type.
 *
 * Returns null when the agent doesn't appear in any workflow; callers fall
 * back to the agent's own `model_selection` (no task-typed defaults apply).
 */
fun primaryTaskFor(workflows: List<Workflow>, agentId: String): String? {
    for (wf in workflows) {
        for (step in wf.steps) {
            if (step.agent == agentId) return step.task
        }
    }
    return null
}

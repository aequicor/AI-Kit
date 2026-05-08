package com.aikit.setup.model

/**
 * Typed view over a parsed manifest.
 *
 * Keeps only fields the generator actually consumes — anything not represented
 * here is still available through the underlying [com.aikit.setup.manifest.RawNode]
 * for validators that need raw access. The intent is that adding a generator
 * feature means extending this type, never reaching into raw nodes from the
 * generator code.
 */
data class TypedManifest(
    val manifestVersion: String,
    val kitVersion: String?,
    val languageCode: String,
    val project: Project,
    val stack: Stack,
    val modules: List<Module>,
    val targets: List<Target>,
    val renderTargets: List<String>,
    val providers: List<Provider>,
    val models: List<Model>,
    val taskTypes: List<TaskTypeRule>,
    val promptDialects: List<PackagePointer>,
    val targetAdapters: List<PackagePointer>,
    val sharedPath: String?,
    val agents: List<Agent>,
    val tools: List<ToolEntry>,
    val workflows: List<Workflow>,
    val knowledge: Knowledge?,
    val policies: Policies,
)

/** Project-wide task-type defaults declared under `task_types:`. */
data class TaskTypeRule(
    val id: String,
    val prefers: ModelTier?,
    val minTier: ModelTier?,
    val needs: List<String>,
)

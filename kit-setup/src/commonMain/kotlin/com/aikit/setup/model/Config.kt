package com.aikit.setup.model

data class ProjectConfig(
    val projectName: String,
    val projectPath: String,
    val language: Language,
    val framework: String?,
    val aiProvider: AiProvider,
    val model: String,
    val enablePlanning: Boolean,
    val enableAgents: Boolean,
)

enum class Language(val displayName: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    PYTHON("Python"),
    TYPESCRIPT("TypeScript"),
    GO("Go"),
    RUST("Rust"),
    OTHER("Other"),
}

enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    OPENCODE("OpenCode"),
    BOTH("Both"),
}

data class AgentDefinition(
    val name: String,
    val description: String,
    val model: String,
    val systemPrompt: String,
)

val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-6"
val DEFAULT_OPENCODE_MODEL = "claude-sonnet-4-6"

val DEFAULT_AGENTS = listOf(
    AgentDefinition(
        name = "orchestrator",
        description = "Coordinates other agents and manages the overall workflow",
        model = DEFAULT_CLAUDE_MODEL,
        systemPrompt = "You are the orchestrator agent. Your job is to break down tasks, " +
            "delegate to specialist agents, and ensure coherent delivery.",
    ),
    AgentDefinition(
        name = "planner",
        description = "Creates detailed implementation plans for features",
        model = DEFAULT_CLAUDE_MODEL,
        systemPrompt = "You are the planner agent. Create detailed, step-by-step implementation " +
            "plans. Write plans to .planning/tasks/<slug>.md.",
    ),
    AgentDefinition(
        name = "implementer",
        description = "Writes production-ready code following the plan",
        model = DEFAULT_CLAUDE_MODEL,
        systemPrompt = "You are the implementer agent. Write clean, production-ready code " +
            "strictly following the plan. No scope drift.",
    ),
    AgentDefinition(
        name = "reviewer",
        description = "Reviews code for quality, security, and correctness",
        model = DEFAULT_CLAUDE_MODEL,
        systemPrompt = "You are the code reviewer agent. Review for correctness, security, " +
            "performance, and adherence to project conventions.",
    ),
    AgentDefinition(
        name = "tester",
        description = "Writes and runs tests to verify functionality",
        model = DEFAULT_CLAUDE_MODEL,
        systemPrompt = "You are the tester agent. Write comprehensive tests covering happy path, " +
            "edge cases, and error scenarios.",
    ),
)

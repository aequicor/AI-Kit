package com.aikit.setup.model

/** Top-level project identity declared at `project:` in the manifest. */
data class Project(
    val name: String,
    val slug: String,
    val description: String?,
    val repoUrl: String?,
    val owners: List<String>,
)

/** Build/test/lint command surface declared at `stack:` in the manifest. */
data class Stack(
    val languages: List<String>,
    val frameworks: List<String>,
    val buildCommand: String?,
    val compileCommand: String?,
    val lintCommand: String?,
    val testCommand: String?,
    val formatCommand: String?,
    val runCommand: String?,
)

/** A logical sub-module of the target project. */
data class Module(
    val name: String,
    val sourceRoot: String?,
    val testRoot: String?,
    val docsPath: String?,
    val responsibility: String?,
)

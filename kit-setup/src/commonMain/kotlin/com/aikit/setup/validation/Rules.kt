package com.aikit.setup.validation

/**
 * Returns the default rule set applied by both `verify` and the pre-flight
 * step of `generate`.
 *
 * Empty for now — rules are added incrementally as the manifest schema firms
 * up. Each rule keeps its own error codes so agents can rely on stable
 * identifiers across releases.
 */
fun defaultRules(): List<ValidationRule> = emptyList()

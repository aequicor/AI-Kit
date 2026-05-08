package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * Single validation check. Each rule encodes one schema constraint (a
 * required field, a type-correctness check, a cross-reference) and reports
 * any number of [ValidationError]s it discovers.
 *
 * Rules are pure: they don't share state and don't perform I/O. The runner
 * applies them in declaration order, but no rule should rely on that — every
 * rule must be safe to run independently of every other.
 */
interface ValidationRule {

    /**
     * Inspects the [manifest] and returns errors found by this rule. Returns
     * an empty list if the rule passes.
     */
    fun check(manifest: Manifest): List<ValidationError>
}

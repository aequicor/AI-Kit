package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * Default [Validator] implementation that walks a flat list of
 * [ValidationRule]s and aggregates their errors.
 *
 * Keeping the runner trivial is intentional: schema concerns live in the
 * rules, not here, so adding or removing a rule never requires editing the
 * runner. The list is captured by reference, not copied — callers that mutate
 * it after construction will see the change.
 */
class RuleBasedValidator(
    private val rules: List<ValidationRule>,
) : Validator {

    override fun validate(manifest: Manifest): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        for (rule in rules) {
            errors += rule.check(manifest)
        }
        return ValidationResult(errors)
    }
}

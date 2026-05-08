package com.aikit.setup.command

import com.aikit.setup.manifest.LoadErrorCode
import com.aikit.setup.validation.ValidationResult

/**
 * Result of running the `verify` use case.
 *
 * Distinguishes load failure (manifest missing, unreadable, malformed) from
 * validation outcome (errors discovered by the rule set). Renderers map this
 * type to JSON; commands map it to an exit code. Both consumers branch on
 * the same shape, so the use-case logic is shared.
 */
sealed class VerifyOutcome {

    /** Manifest could not be loaded; validation never ran. */
    data class LoadFailure(
        val path: String,
        val code: LoadErrorCode,
        val message: String,
    ) : VerifyOutcome()

    /** Manifest loaded; validation produced [result]. */
    data class Validated(val result: ValidationResult) : VerifyOutcome()
}

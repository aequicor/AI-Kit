package com.aikit.setup.command

import com.aikit.setup.generation.GenerationResult
import com.aikit.setup.manifest.LoadErrorCode
import com.aikit.setup.validation.ValidationResult

/**
 * Result of running the `generate` use case.
 *
 * The three branches mirror the steps of the generate flow: load the
 * manifest, validate it, then run the generator. Each step has its own
 * failure mode, and renderers/commands need to react differently to each
 * (different JSON shape, different exit code), so the type encodes the
 * stage that succeeded last.
 */
sealed class GenerateOutcome {

    /** Manifest could not be loaded; nothing was validated or generated. */
    data class LoadFailure(
        val path: String,
        val code: LoadErrorCode,
        val message: String,
    ) : GenerateOutcome()

    /** Manifest loaded but failed validation; generation was refused. */
    data class Invalid(val result: ValidationResult) : GenerateOutcome()

    /** Manifest validated; generator ran and returned [result]. */
    data class Generated(val result: GenerationResult) : GenerateOutcome()
}

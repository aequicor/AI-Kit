package com.aikit.setup.command

import com.aikit.setup.output.Console
import com.aikit.setup.output.GenerateResultRenderer

/**
 * CLI adapter for the `generate` subcommand.
 *
 * Mirrors [VerifyCommand] in shape — delegates the use case to
 * [GenerateService], output formatting to [GenerateResultRenderer], and
 * maps the typed outcome to an exit code.
 *
 * Exit codes:
 *  - `0` — generation succeeded
 *  - `1` — manifest invalid; generation refused (output is the verify-style JSON)
 *  - `2` — load failure or generation runtime error
 */
class GenerateCommand(
    private val service: GenerateService,
    private val renderer: GenerateResultRenderer,
    private val console: Console,
) {
    /**
     * Runs generate against the manifest at [manifestPath] and returns the
     * exit code the binary should terminate with.
     */
    fun run(manifestPath: String): Int {
        val outcome = service.generate(manifestPath)
        console.writeLine(renderer.render(outcome))
        return exitCodeFor(outcome)
    }

    private fun exitCodeFor(outcome: GenerateOutcome): Int = when (outcome) {
        is GenerateOutcome.LoadFailure -> 2
        is GenerateOutcome.Invalid -> 1
        is GenerateOutcome.Generated -> if (outcome.result.success) 0 else 2
    }
}

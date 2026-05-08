package com.aikit.setup.command

import com.aikit.setup.output.Console
import com.aikit.setup.output.VerifyResultRenderer

/**
 * CLI adapter for the `verify` subcommand.
 *
 * Three responsibilities, one each:
 *  1. delegate to [VerifyService] for the use case,
 *  2. delegate to [VerifyResultRenderer] for output formatting,
 *  3. translate the outcome to a process exit code and write the rendered
 *     output via [Console].
 *
 * Exit codes:
 *  - `0` — manifest valid
 *  - `1` — manifest invalid (one or more validation errors)
 *  - `2` — manifest could not be loaded (missing, unreadable, malformed)
 */
class VerifyCommand(
    private val service: VerifyService,
    private val renderer: VerifyResultRenderer,
    private val console: Console,
) {
    /**
     * Runs verify against the manifest at [manifestPath] and returns the
     * exit code the binary should terminate with.
     */
    fun run(manifestPath: String): Int {
        val outcome = service.verify(manifestPath)
        console.writeLine(renderer.render(outcome))
        return exitCodeFor(outcome)
    }

    private fun exitCodeFor(outcome: VerifyOutcome): Int = when (outcome) {
        is VerifyOutcome.LoadFailure -> 2
        is VerifyOutcome.Validated -> if (outcome.result.valid) 0 else 1
    }
}

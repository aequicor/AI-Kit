package com.aikit.setup.output

import com.aikit.setup.command.VerifyOutcome
import com.aikit.setup.validation.ValidationError

/**
 * Renders a [VerifyOutcome] to a string suitable for emission on stdout.
 *
 * The interface lets commands swap output formats (JSON today, anything else
 * tomorrow) without touching the use-case code. The [JsonVerifyResultRenderer]
 * implementation defines the agent-facing wire format.
 */
interface VerifyResultRenderer {

    /** Returns the rendered representation of [outcome]. */
    fun render(outcome: VerifyOutcome): String
}

/**
 * Renders verify outcomes as JSON consumed by orchestrating agents.
 *
 * Wire format:
 * ```
 * { "valid": bool,
 *   "errors": [ { "path": "...", "code": "...", "message": "...", "hint": "..."? }, ... ] }
 * ```
 *
 * Load failures collapse to the same shape with a single error whose `code`
 * is the lowercase form of [com.aikit.setup.manifest.LoadErrorCode] — that
 * way agents have a single output schema to parse regardless of which stage
 * failed.
 */
class JsonVerifyResultRenderer : VerifyResultRenderer {

    override fun render(outcome: VerifyOutcome): String = when (outcome) {
        is VerifyOutcome.LoadFailure -> Json.encode(
            mapOf(
                "valid" to false,
                "errors" to listOf(
                    mapOf(
                        "path" to outcome.path,
                        "code" to outcome.code.name.lowercase(),
                        "message" to outcome.message,
                    ),
                ),
            ),
        )
        is VerifyOutcome.Validated -> Json.encode(
            mapOf(
                "valid" to outcome.result.valid,
                "errors" to outcome.result.errors.map(::errorToMap),
            ),
        )
    }

    private fun errorToMap(e: ValidationError): Map<String, Any?> = buildMap {
        put("path", e.path)
        put("code", e.code)
        put("message", e.message)
        if (e.hint != null) put("hint", e.hint)
    }
}

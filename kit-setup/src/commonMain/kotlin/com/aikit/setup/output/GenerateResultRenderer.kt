package com.aikit.setup.output

import com.aikit.setup.command.GenerateOutcome
import com.aikit.setup.generation.GenerationError
import com.aikit.setup.validation.ValidationError

/**
 * Renders a [GenerateOutcome] to a string suitable for emission on stdout.
 *
 * Generation has three terminal shapes (load failure, validation failure,
 * generator ran) — the renderer is the single place that knows how each maps
 * to the wire format consumed by the orchestrating agent.
 */
interface GenerateResultRenderer {

    /** Returns the rendered representation of [outcome]. */
    fun render(outcome: GenerateOutcome): String
}

/**
 * Renders generate outcomes as JSON.
 *
 * Wire format by branch:
 * - load failure / validation failure → identical to the verify renderer
 *   (`{"valid": false, "errors": [...]}`) so agents can reuse the same parser
 * - generator ran → `{"ok": bool, "generated": [...], "errors": [...]?}`
 */
class JsonGenerateResultRenderer : GenerateResultRenderer {

    override fun render(outcome: GenerateOutcome): String = when (outcome) {
        is GenerateOutcome.LoadFailure -> Json.encode(
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
        is GenerateOutcome.Invalid -> Json.encode(
            mapOf(
                "valid" to outcome.result.valid,
                "errors" to outcome.result.errors.map(::validationErrorToMap),
            ),
        )
        is GenerateOutcome.Generated -> Json.encode(
            buildMap {
                put("ok", outcome.result.success)
                put("generated", outcome.result.generatedFiles)
                if (outcome.result.errors.isNotEmpty()) {
                    put("errors", outcome.result.errors.map(::generationErrorToMap))
                }
            },
        )
    }

    private fun validationErrorToMap(e: ValidationError): Map<String, Any?> = buildMap {
        put("path", e.path)
        put("code", e.code)
        put("message", e.message)
        if (e.hint != null) put("hint", e.hint)
    }

    private fun generationErrorToMap(e: GenerationError): Map<String, Any?> = mapOf(
        "path" to e.path,
        "code" to e.code,
        "message" to e.message,
    )
}

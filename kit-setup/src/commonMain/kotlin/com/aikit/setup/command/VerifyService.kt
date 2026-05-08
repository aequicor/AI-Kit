package com.aikit.setup.command

import com.aikit.setup.manifest.LoadResult
import com.aikit.setup.manifest.ManifestLoader
import com.aikit.setup.validation.Validator

/**
 * Pure use case for the `verify` subcommand: load the manifest, validate it,
 * return a typed [VerifyOutcome]. No I/O beyond what the loader already
 * performs, no exit-code mapping, no output formatting — those concerns
 * belong to the command adapter and the renderer.
 */
class VerifyService(
    private val loader: ManifestLoader,
    private val validator: Validator,
) {

    /** Runs verify against the manifest at [manifestPath]. */
    fun verify(manifestPath: String): VerifyOutcome {
        return when (val loaded = loader.load(manifestPath)) {
            is LoadResult.Failure -> VerifyOutcome.LoadFailure(
                path = manifestPath,
                code = loaded.code,
                message = loaded.message,
            )
            is LoadResult.Success -> VerifyOutcome.Validated(validator.validate(loaded.manifest))
        }
    }
}

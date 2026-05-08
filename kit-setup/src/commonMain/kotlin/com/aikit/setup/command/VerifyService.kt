package com.aikit.setup.command

import com.aikit.setup.manifest.LoadResult
import com.aikit.setup.manifest.ManifestLoader
import com.aikit.setup.manifest.profile.ProfileResolver
import com.aikit.setup.validation.ValidationResult
import com.aikit.setup.validation.Validator

/**
 * Pure use case for the `verify` subcommand: load the manifest, resolve any
 * profiles declared under `stack.profiles[]`, then validate the merged
 * manifest. Profile-resolution failures (unknown name, axis mismatch, field
 * trespass, cardinality) flow through the same [VerifyOutcome.Validated]
 * channel as ordinary validation errors so the rendered output stays uniform.
 *
 * No I/O beyond what the loader already performs, no exit-code mapping, no
 * output formatting — those concerns belong to the command adapter and the
 * renderer.
 */
class VerifyService(
    private val loader: ManifestLoader,
    private val profileResolver: ProfileResolver,
    private val validator: Validator,
) {

    /** Runs verify against the manifest at [manifestPath]. */
    fun verify(manifestPath: String): VerifyOutcome {
        val loaded = loader.load(manifestPath)
        if (loaded is LoadResult.Failure) {
            return VerifyOutcome.LoadFailure(
                path = manifestPath,
                code = loaded.code,
                message = loaded.message,
            )
        }
        val manifest = (loaded as LoadResult.Success).manifest
        return when (val resolved = profileResolver.resolve(manifest)) {
            is ProfileResolver.Result.Failure -> VerifyOutcome.Validated(ValidationResult(resolved.errors))
            is ProfileResolver.Result.Success -> VerifyOutcome.Validated(validator.validate(resolved.manifest))
        }
    }
}

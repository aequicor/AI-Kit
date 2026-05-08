package com.aikit.setup.command

import com.aikit.setup.generation.GenerationError
import com.aikit.setup.generation.GenerationResult
import com.aikit.setup.generation.KitGenerator
import com.aikit.setup.manifest.LoadResult
import com.aikit.setup.manifest.ManifestLoader
import com.aikit.setup.validation.Validator

/**
 * Pure use case for the `generate` subcommand: load the manifest, validate
 * it, and (only if valid) hand it to the kit generator. Returns a typed
 * [GenerateOutcome]; the calling command formats and exits.
 *
 * The service decides the target root from the manifest path: the manifest
 * lives under `<targetRoot>/.aikit/manifest.yaml`, so the root is the
 * grandparent directory. Falls back to `"."` when the path doesn't follow
 * that convention.
 */
class GenerateService(
    private val loader: ManifestLoader,
    private val validator: Validator,
    private val generator: KitGenerator,
) {

    /** Runs generate against the manifest at [manifestPath]. */
    fun generate(manifestPath: String): GenerateOutcome {
        val loaded = loader.load(manifestPath)
        if (loaded is LoadResult.Failure) {
            return GenerateOutcome.LoadFailure(
                path = manifestPath,
                code = loaded.code,
                message = loaded.message,
            )
        }
        val manifest = (loaded as LoadResult.Success).manifest

        val validation = validator.validate(manifest)
        if (!validation.valid) {
            return GenerateOutcome.Invalid(validation)
        }

        val targetRoot = inferTargetRoot(manifestPath)
        val result = try {
            generator.generate(manifest, targetRoot)
        } catch (e: Throwable) {
            val msg = e.message ?: e::class.simpleName ?: "unknown error"
            GenerationResult(
                generatedFiles = emptyList(),
                errors = listOf(
                    GenerationError(path = "", code = "generator_failed", message = msg),
                ),
            )
        }
        return GenerateOutcome.Generated(result)
    }

    private fun inferTargetRoot(manifestPath: String): String {
        val normalized = manifestPath.replace('\\', '/').trimEnd('/')
        val parent = normalized.substringBeforeLast('/', "")
        if (parent.isEmpty()) return "."
        if (parent.endsWith("/.aikit") || parent == ".aikit") {
            val grand = parent.substringBeforeLast('/', "")
            return if (grand.isEmpty()) "." else grand
        }
        return parent
    }
}

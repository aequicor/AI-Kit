package com.aikit.setup.manifest

import com.aikit.setup.io.FileReader

/**
 * File-backed [ManifestLoader] that reads YAML from disk via a [FileReader]
 * and delegates parsing to a pluggable [YamlParser].
 *
 * Each stage's failure mode maps to a distinct [LoadErrorCode], keeping the
 * machine-readable verify output unambiguous.
 */
class DefaultManifestLoader(
    private val files: FileReader,
    private val parser: YamlParser,
) : ManifestLoader {

    override fun load(path: String): LoadResult {
        if (!files.exists(path)) {
            return LoadResult.Failure(
                code = LoadErrorCode.MANIFEST_NOT_FOUND,
                message = "Manifest file not found: $path",
            )
        }
        val content = try {
            files.readFile(path)
        } catch (e: Throwable) {
            return LoadResult.Failure(
                code = LoadErrorCode.READ_FAILED,
                message = "Failed to read manifest at $path: ${describe(e)}",
            )
        }
        val root = try {
            parser.parse(content)
        } catch (e: Throwable) {
            return LoadResult.Failure(
                code = LoadErrorCode.PARSE_FAILED,
                message = "Failed to parse YAML at $path: ${describe(e)}",
            )
        }
        return LoadResult.Success(Manifest(root))
    }

    private fun describe(t: Throwable): String = t.message ?: t::class.simpleName ?: "unknown error"
}

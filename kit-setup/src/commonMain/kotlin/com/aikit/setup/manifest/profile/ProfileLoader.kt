package com.aikit.setup.manifest.profile

import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.YamlParser
import com.aikit.setup.manifest.field
import com.aikit.setup.manifest.stringOrNull
import com.aikit.setup.templates.TemplateRegistry
import com.aikit.setup.validation.ValidationError

/**
 * Locates a profile by bare name (e.g. `solid`, `kotlin-gradle`), reads its
 * YAML, parses it, and returns a [Profile] ready for axis-field check and
 * merge — or a list of [ValidationError]s explaining why it could not.
 *
 * Search order is `profiles/<axis>/<name>.yaml` for every axis dir. A name
 * may match in only one axis; the loader stops at the first match.
 */
class ProfileLoader(
    private val templates: TemplateRegistry,
    private val parser: YamlParser,
) {

    sealed class Result {
        data class Found(val profile: Profile) : Result()
        data class Failure(val errors: List<ValidationError>) : Result()
    }

    fun load(name: String): Result {
        val located = locate(name)
            ?: return Result.Failure(
                listOf(
                    ValidationError(
                        path = "/stack/profiles",
                        code = "profile_not_found",
                        message = "Profile `$name` not found under templates/profiles/",
                        hint = "Create profiles/<axis>/$name.yaml or remove the entry from stack.profiles.",
                    ),
                ),
            )

        val (axisFromDir, content) = located
        val raw = try {
            parser.parse(content)
        } catch (e: Throwable) {
            return Result.Failure(
                listOf(
                    ValidationError(
                        path = profilePath(axisFromDir, name),
                        code = "profile_parse_failed",
                        message = "Failed to parse profile `$name`: ${describe(e)}",
                    ),
                ),
            )
        }

        if (raw !is RawNode.Mapping) {
            return Result.Failure(
                listOf(
                    ValidationError(
                        path = profilePath(axisFromDir, name),
                        code = "profile_root_not_mapping",
                        message = "Profile `$name` root must be a YAML mapping.",
                    ),
                ),
            )
        }

        val declaredAxisRaw = raw.field("_profile_axis").stringOrNull()
        val declaredAxis = declaredAxisRaw?.let { ProfileAxis.fromDirName(it) }
        if (declaredAxis == null || declaredAxis != axisFromDir) {
            return Result.Failure(
                listOf(
                    ValidationError(
                        path = profilePath(axisFromDir, name),
                        code = "profile_axis_mismatch",
                        message = "Profile `$name` lives under `${axisFromDir.dirName}/` but declares " +
                            "`_profile_axis: ${declaredAxisRaw ?: "(missing)"}`.",
                        hint = "Set _profile_axis: ${axisFromDir.dirName} or move the file to profiles/" +
                            "${declaredAxisRaw ?: "<axis>"}/.",
                    ),
                ),
            )
        }

        val declaredName = raw.field("_profile_name").stringOrNull()
        if (declaredName != null && declaredName != name) {
            return Result.Failure(
                listOf(
                    ValidationError(
                        path = profilePath(axisFromDir, name),
                        code = "profile_name_mismatch",
                        message = "Profile filename is `$name` but declares `_profile_name: $declaredName`.",
                    ),
                ),
            )
        }

        val description = raw.field("_profile_description").stringOrNull()
        val body = stripMetadata(raw)
        return Result.Found(
            Profile(
                name = name,
                axis = axisFromDir,
                description = description,
                body = body,
            ),
        )
    }

    /**
     * Walks every axis directory looking for `<name>.yaml`. Returns the axis
     * derived from the directory plus the file contents. Null on miss.
     */
    private fun locate(name: String): Pair<ProfileAxis, String>? {
        for (axis in ProfileAxis.entries) {
            val path = "profiles/${axis.dirName}/$name.yaml"
            val content = templates.read(path)
            if (content != null) return axis to content
        }
        return null
    }

    private fun stripMetadata(node: RawNode.Mapping): RawNode.Mapping {
        val filtered = node.entries.filterKeys { !it.startsWith("_profile_") }
        return RawNode.Mapping(filtered)
    }

    private fun profilePath(axis: ProfileAxis, name: String): String =
        "/profiles/${axis.dirName}/$name.yaml"

    private fun describe(t: Throwable): String =
        t.message ?: t::class.simpleName ?: "unknown error"
}

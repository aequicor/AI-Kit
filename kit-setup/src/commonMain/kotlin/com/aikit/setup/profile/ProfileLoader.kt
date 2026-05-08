package com.aikit.setup.profile

import com.aikit.setup.embedded.EmbeddedTemplates
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.YamlParser

/**
 * Loads profile YAMLs from the embedded templates, normalises them into
 * [Profile] records, and validates that the declared `_profile_axis` matches
 * the directory the file is filed under.
 *
 * The loader only resolves profiles referenced from the manifest's
 * `stack.profiles` list. It does not attempt to merge profile data into the
 * manifest — that is the orchestrating agent's job during manifest
 * authoring. The generator only needs structural metadata for host-axis
 * profiles plus a way to assert that all declared profiles actually exist.
 */
class ProfileLoader(private val parser: YamlParser) {

    fun loadAll(profileNames: List<String>): ProfileLoadResult {
        val nameIndex: Map<String, String> = EmbeddedTemplates.files.keys
            .filter { it.startsWith("profiles/") && it.endsWith(".yaml") }
            .associateBy { profileBasename(it) }
        val profiles = mutableListOf<Profile>()
        val errors = mutableListOf<ProfileLoadError>()
        for (name in profileNames) {
            val path = nameIndex[name]
            if (path == null) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_not_found",
                    message = "Profile '$name' was not found in the embedded profiles directory.",
                )
                continue
            }
            val axisDir = path.removePrefix("profiles/").substringBefore('/')
            val axis = ProfileAxis.fromDir(axisDir)
            if (axis == null) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_axis_unknown",
                    message = "Profile '$name' is filed under unrecognized axis '$axisDir'.",
                )
                continue
            }
            val content = EmbeddedTemplates.files[path]!!
            val root = try {
                parser.parse(content)
            } catch (t: Throwable) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_parse_failed",
                    message = "Failed to parse profile '$name' at $path: ${t.message ?: t::class.simpleName}",
                )
                continue
            }
            val mapping = (root as? RawNode.Mapping)?.entries
            if (mapping == null) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_root_invalid",
                    message = "Profile '$name' root is not a YAML mapping.",
                )
                continue
            }
            val declaredAxis = (mapping["_profile_axis"] as? RawNode.Scalar)?.value
            if (declaredAxis == null) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_axis_missing",
                    message = "Profile '$name' is missing _profile_axis.",
                )
                continue
            }
            if (declaredAxis != axis.dir) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_axis_mismatch",
                    message = "Profile '$name' declares _profile_axis='$declaredAxis' but is filed under '$axisDir'.",
                )
                continue
            }
            val description = (mapping["_profile_description"] as? RawNode.Scalar)?.value ?: ""
            val host = if (axis == ProfileAxis.HOST) decodeHost(mapping["host"]) else null
            if (axis == ProfileAxis.HOST && host == null) {
                errors += ProfileLoadError(
                    profileName = name,
                    code = "profile_host_block_missing",
                    message = "Host profile '$name' is missing the required `host:` block.",
                )
                continue
            }
            profiles += Profile(
                name = (mapping["_profile_name"] as? RawNode.Scalar)?.value ?: name,
                axis = axis,
                description = description,
                host = host,
            )
        }
        return ProfileLoadResult(profiles, errors)
    }

    private fun decodeHost(node: RawNode?): HostMetadata? {
        val map = (node as? RawNode.Mapping)?.entries ?: return null
        val templateDir = (map["template_dir"] as? RawNode.Scalar)?.value ?: return null
        val configFile = (map["config_file"] as? RawNode.Scalar)?.value ?: return null
        val agentFormat = (map["agent_format"] as? RawNode.Scalar)?.value ?: return null
        val instructionFile = (map["instruction_file"] as? RawNode.Scalar)?.value ?: return null
        return HostMetadata(templateDir, configFile, agentFormat, instructionFile)
    }

    private fun profileBasename(path: String): String =
        path.substringAfterLast('/').removeSuffix(".yaml")
}

data class ProfileLoadResult(
    val profiles: List<Profile>,
    val errors: List<ProfileLoadError>,
) {
    fun byAxis(axis: ProfileAxis): List<Profile> = profiles.filter { it.axis == axis }
    fun byName(name: String): Profile? = profiles.firstOrNull { it.name == name }
}

data class ProfileLoadError(val profileName: String, val code: String, val message: String)

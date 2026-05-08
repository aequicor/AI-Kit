package com.aikit.setup.profile

/**
 * Decoded view of a profile YAML from `templates/profiles/<axis>/<name>.yaml`.
 *
 * The binary only consumes a small slice of profile data — host-axis profiles
 * carry the rendering metadata that drives the generator (template tree to
 * use, top-level config file path, agent frontmatter dialect, instruction
 * file). Other axes' fields (lsp, formatter, code_quality, …) are merged
 * into the manifest by the orchestrating agent during authoring, so the
 * generator reads them from there rather than re-merging at install time.
 */
data class Profile(
    val name: String,
    val axis: ProfileAxis,
    val description: String,
    val host: HostMetadata?,
)

enum class ProfileAxis(val dir: String) {
    LANGUAGE("language"),
    FRAMEWORK("framework"),
    HOST("host"),
    PROVIDER("provider"),
    CAPABILITY("capability");

    companion object {
        fun fromDir(dir: String): ProfileAxis? = entries.firstOrNull { it.dir == dir }
    }
}

/**
 * Host-axis profile fields. Drives where each host's template tree is read
 * from and which top-level config file gets written into the target.
 */
data class HostMetadata(
    val templateDir: String,
    val configFile: String,
    val agentFormat: String,
    val instructionFile: String,
)

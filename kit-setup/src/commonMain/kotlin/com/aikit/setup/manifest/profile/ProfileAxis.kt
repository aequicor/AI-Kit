package com.aikit.setup.manifest.profile

/**
 * Closed set of profile axes. The directory name under `templates/profiles/`
 * is the canonical form (`language` / `framework` / `capability`); each
 * profile YAML's `_profile_axis:` field must round-trip through here.
 */
enum class ProfileAxis(val dirName: String) {
    LANGUAGE("language"),
    FRAMEWORK("framework"),
    CAPABILITY("capability"),
    ;

    companion object {
        fun fromDirName(name: String): ProfileAxis? =
            entries.firstOrNull { it.dirName == name }
    }
}

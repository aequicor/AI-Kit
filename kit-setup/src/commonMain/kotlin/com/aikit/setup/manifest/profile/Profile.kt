package com.aikit.setup.manifest.profile

import com.aikit.setup.manifest.RawNode

/**
 * A profile loaded from `templates/profiles/<axis>/<name>.yaml`, ready to be
 * merged into a manifest. The body has had the `_profile_*` metadata stripped
 * already so deep-merge does not re-introduce those keys into the final manifest.
 */
data class Profile(
    /** Filename basename, stable across `_profile_name` field too. */
    val name: String,

    /** Axis derived from the parent directory. The YAML's `_profile_axis` must agree. */
    val axis: ProfileAxis,

    /** Optional human-readable description from `_profile_description:`. */
    val description: String?,

    /** Profile body with `_profile_*` keys removed, ready for deep-merge. */
    val body: RawNode.Mapping,
)

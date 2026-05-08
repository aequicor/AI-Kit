package com.aikit.setup.generation

import com.aikit.setup.profile.HostMetadata

/**
 * Per-host rendering context. Pairs the host's structural metadata (where
 * its template tree lives, which top-level config file to write, which
 * frontmatter dialect its agent files use) with the substitution variables
 * that all `{{VAR}}` placeholders in templates expand against.
 *
 * Multi-host installs build one [HostContext] per host name so each tree
 * renders independently with the correct overlay applied.
 */
data class HostContext(
    val hostName: String,
    val metadata: HostMetadata,
    val variables: Map<String, String>,
)

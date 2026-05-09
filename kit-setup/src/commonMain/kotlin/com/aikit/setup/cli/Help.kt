package com.aikit.setup.cli

/**
 * Binary version reported by `--version` and embedded in the help text.
 * Kept in source rather than read from build metadata so the help text is
 * available without runtime resource lookup on Kotlin/Native.
 */
const val KIT_SETUP_VERSION: String = "2.5.0"

/**
 * Returns the human-readable help text printed by `--help` and on usage
 * errors. Plain text by design — it's for humans, not the orchestrating
 * agent (which reads the JSON output from `verify`/`generate` instead).
 */
fun helpText(): String = """
    kit-setup — AI agent kit configurator

    USAGE:
        kit-setup <subcommand> [<args>...]

    SUBCOMMANDS:
        verify     Validate a manifest. Emits machine-readable JSON to stdout.
        generate   Generate the kit from a validated manifest. Overwrites existing files.
        schema     List variants bundled in this binary (agents, dialects, adapters, ...).

    ARGUMENTS:
        <manifest-path>      Path to the manifest file (verify/generate).
                             Defaults to "$DEFAULT_MANIFEST_PATH".
        --format json|human  Output format for 'schema'. Defaults to 'json'.

    OPTIONS:
        -h, --help        Show this help.
        -v, --version     Show the version.

    EXIT CODES:
        0   Success.
        1   Manifest is invalid (verify) or generation was refused due to invalid manifest.
        2   Usage error or runtime failure (missing file, parse error, I/O failure).
""".trimIndent()

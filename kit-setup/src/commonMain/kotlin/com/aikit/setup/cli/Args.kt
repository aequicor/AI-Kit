package com.aikit.setup.cli

/**
 * Default location of the kit manifest, relative to the working directory
 * the binary is invoked from. Conventionally the binary is run from the
 * target project's root, so the manifest lives at `<project>/.aikit/manifest.yaml`.
 */
const val DEFAULT_MANIFEST_PATH: String = ".aikit/manifest.yaml"

/**
 * Parsed view of the command line.
 *
 * The CLI surface is deliberately tiny: two subcommands plus help/version.
 * Anything that doesn't match collapses into [Error]; the dispatcher in
 * `Main` then prints the message and exits with status `2`.
 */
sealed class Command {

    /** `kit-setup verify [<path>]` */
    data class Verify(val manifestPath: String) : Command()

    /** `kit-setup generate [<path>]` */
    data class Generate(val manifestPath: String) : Command()

    /** `kit-setup --help` (or no arguments). */
    data object Help : Command()

    /** `kit-setup --version`. */
    data object Version : Command()

    /** Anything that didn't parse: unknown subcommand, stray flags, etc. */
    data class Error(val message: String) : Command()
}

/**
 * Parses raw argv into a [Command]. The grammar is small enough that a
 * hand-written parser stays clearer than pulling in a library.
 */
object Args {

    /**
     * Returns the [Command] described by [argv]. Always succeeds — any
     * malformed input becomes [Command.Error] rather than throwing.
     */
    fun parse(argv: Array<String>): Command {
        if (argv.isEmpty()) return Command.Help
        val first = argv[0]
        if (first == "-h" || first == "--help") return Command.Help
        if (first == "-v" || first == "--version") return Command.Version

        return when (first) {
            "verify" -> parsePathSubcommand("verify", argv) { Command.Verify(it) }
            "generate" -> parsePathSubcommand("generate", argv) { Command.Generate(it) }
            else -> Command.Error("Unknown subcommand: '$first'. Run 'kit-setup --help' for usage.")
        }
    }

    private inline fun parsePathSubcommand(
        name: String,
        argv: Array<String>,
        build: (String) -> Command,
    ): Command {
        val rest = argv.drop(1)
        if (rest.any { it.startsWith("-") }) {
            return Command.Error(
                "'$name' takes only an optional manifest path. Unrecognized flag in: ${rest.joinToString(" ")}",
            )
        }
        if (rest.size > 1) {
            return Command.Error("'$name' takes at most one positional argument (manifest path).")
        }
        val path = rest.firstOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_MANIFEST_PATH
        return build(path)
    }
}

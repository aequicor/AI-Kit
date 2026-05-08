package com.aikit.setup.cli

/**
 * Default location of the kit manifest, relative to the working directory
 * the binary is invoked from. Conventionally the binary is run from the
 * target project's root, so the manifest lives at `<project>/.aikit/manifest.yaml`.
 */
const val DEFAULT_MANIFEST_PATH: String = ".aikit/manifest.yaml"

/**
 * Output format for the `schema` subcommand.
 *
 * `JSON` is the agent-facing wire format; `HUMAN` is a plain-text tree for
 * humans inspecting the bundle. Default is `JSON` to stay consistent with
 * the rest of the CLI surface.
 */
enum class SchemaFormat { JSON, HUMAN }

/**
 * Parsed view of the command line.
 *
 * The CLI surface is deliberately tiny: three subcommands plus help/version.
 * Anything that doesn't match collapses into [Error]; the dispatcher in
 * `Main` then prints the message and exits with status `2`.
 */
sealed class Command {

    /** `kit-setup verify [<path>]` */
    data class Verify(val manifestPath: String) : Command()

    /** `kit-setup generate [<path>]` */
    data class Generate(val manifestPath: String) : Command()

    /** `kit-setup schema [--format json|human]` */
    data class Schema(val format: SchemaFormat) : Command()

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
            "schema" -> parseSchema(argv)
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

    /**
     * `schema` accepts a single optional `--format json|human` flag and no
     * positional args — the catalog comes from the binary itself, not from
     * a path on disk.
     */
    private fun parseSchema(argv: Array<String>): Command {
        val rest = argv.drop(1)
        var format = SchemaFormat.JSON
        var i = 0
        while (i < rest.size) {
            val arg = rest[i]
            when {
                arg == "--format" -> {
                    val value = rest.getOrNull(i + 1)
                        ?: return Command.Error("'schema --format' requires a value: 'json' or 'human'.")
                    format = parseFormat(value)
                        ?: return Command.Error("Unknown --format value: '$value'. Expected 'json' or 'human'.")
                    i += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter('=')
                    format = parseFormat(value)
                        ?: return Command.Error("Unknown --format value: '$value'. Expected 'json' or 'human'.")
                    i += 1
                }
                arg.startsWith("-") -> {
                    return Command.Error("'schema' does not recognise flag '$arg'. Run 'kit-setup --help'.")
                }
                else -> {
                    return Command.Error("'schema' takes no positional arguments. Got: '$arg'.")
                }
            }
        }
        return Command.Schema(format)
    }

    private fun parseFormat(value: String): SchemaFormat? = when (value.lowercase()) {
        "json" -> SchemaFormat.JSON
        "human" -> SchemaFormat.HUMAN
        else -> null
    }
}

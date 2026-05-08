package com.aikit.setup

import com.aikit.setup.cli.Args
import com.aikit.setup.cli.Command
import com.aikit.setup.cli.KIT_SETUP_VERSION
import com.aikit.setup.cli.helpText
import com.aikit.setup.command.GenerateCommand
import com.aikit.setup.command.GenerateService
import com.aikit.setup.command.VerifyCommand
import com.aikit.setup.command.VerifyService
import com.aikit.setup.generation.DefaultKitGenerator
import com.aikit.setup.generation.KitGenerator
import com.aikit.setup.io.FileSystem
import com.aikit.setup.manifest.DefaultManifestLoader
import com.aikit.setup.manifest.ManifestLoader
import com.aikit.setup.manifest.StubYamlParser
import com.aikit.setup.manifest.YamlParser
import com.aikit.setup.output.Console
import com.aikit.setup.output.GenerateResultRenderer
import com.aikit.setup.output.JsonGenerateResultRenderer
import com.aikit.setup.output.JsonVerifyResultRenderer
import com.aikit.setup.output.VerifyResultRenderer
import com.aikit.setup.validation.RuleBasedValidator
import com.aikit.setup.validation.Validator
import com.aikit.setup.validation.defaultRules

/**
 * Composition root for the kit-setup binary.
 *
 * Holds every collaborator the CLI needs and wires them together so the
 * platform entry points stay trivial: each [Platform.kt][NativeFileSystem]
 * just constructs a [FileSystem] and a [Console] and hands them to the app.
 *
 * The constructor exposes every dependency as a parameter with a default
 * matching the production wiring — production code can call the no-arg
 * convenience constructor `KitSetupApp(fs, console)`, while tests can
 * substitute any individual collaborator (mock loader, in-memory generator,
 * recording console) without touching the rest.
 */
class KitSetupApp(
    private val verifyCommand: VerifyCommand,
    private val generateCommand: GenerateCommand,
    private val console: Console,
) {

    /**
     * Convenience constructor that builds the production wiring from the
     * two host-provided primitives. Keeps platform `main`s a single line.
     */
    constructor(
        files: FileSystem,
        console: Console,
        yamlParser: YamlParser = StubYamlParser(),
        manifestLoader: ManifestLoader = DefaultManifestLoader(files, yamlParser),
        validator: Validator = RuleBasedValidator(defaultRules()),
        kitGenerator: KitGenerator = DefaultKitGenerator(files),
        verifyResultRenderer: VerifyResultRenderer = JsonVerifyResultRenderer(),
        generateResultRenderer: GenerateResultRenderer = JsonGenerateResultRenderer(),
    ) : this(
        verifyCommand = VerifyCommand(
            service = VerifyService(manifestLoader, validator),
            renderer = verifyResultRenderer,
            console = console,
        ),
        generateCommand = GenerateCommand(
            service = GenerateService(manifestLoader, validator, kitGenerator),
            renderer = generateResultRenderer,
            console = console,
        ),
        console = console,
    )

    /**
     * Parses [args], dispatches to the matching command, and returns the
     * exit code the binary should terminate with. Does **not** call
     * `exitProcess` — that's the caller's job, so this method stays testable.
     */
    fun run(args: Array<String>): Int = when (val cmd = Args.parse(args)) {
        is Command.Help -> {
            console.writeLine(helpText())
            0
        }
        is Command.Version -> {
            console.writeLine(KIT_SETUP_VERSION)
            0
        }
        is Command.Verify -> verifyCommand.run(cmd.manifestPath)
        is Command.Generate -> generateCommand.run(cmd.manifestPath)
        is Command.Error -> {
            console.writeLine(cmd.message)
            console.writeLine("")
            console.writeLine(helpText())
            2
        }
    }
}

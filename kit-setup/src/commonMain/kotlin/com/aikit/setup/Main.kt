package com.aikit.setup

import com.aikit.setup.cli.CliParser
import com.aikit.setup.cli.ParseResult
import com.aikit.setup.cli.printHelp
import com.aikit.setup.generator.FileSystem
import com.aikit.setup.generator.ProjectGenerator

fun runSetup(args: Array<String>, fs: FileSystem) {
    val parser = CliParser()
    when (val result = parser.parse(args)) {
        is ParseResult.ShowHelp -> printHelp()
        is ParseResult.Error -> {
            println("Error: ${result.message}")
            println()
            printHelp()
        }
        is ParseResult.Config -> {
            val config = result.config
            println("Generating AI agent configuration for '${config.projectName}'...")
            println("  Provider : ${config.aiProvider.displayName}")
            println("  Language : ${config.language.displayName}${if (config.framework != null) " / ${config.framework}" else ""}")
            println("  Model    : ${config.model}")
            println("  Path     : ${config.projectPath}")
            println()

            val generator = ProjectGenerator(fs)
            val genResult = generator.generate(config)

            if (genResult.generatedFiles.isNotEmpty()) {
                println("Generated files:")
                genResult.generatedFiles.forEach { println("  + $it") }
            }

            if (genResult.errors.isNotEmpty()) {
                println()
                println("Errors:")
                genResult.errors.forEach { println("  ! $it") }
            }

            println()
            if (genResult.success) {
                println("Done. Your project is ready for AI-assisted development.")
            } else {
                println("Completed with ${genResult.errors.size} error(s).")
            }
        }
    }
}

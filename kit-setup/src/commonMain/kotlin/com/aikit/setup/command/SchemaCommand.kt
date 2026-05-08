package com.aikit.setup.command

import com.aikit.setup.cli.SchemaFormat
import com.aikit.setup.output.Console
import com.aikit.setup.output.SchemaResultRenderer

/**
 * CLI adapter for the `schema` subcommand.
 *
 * Three responsibilities, mirroring the verify/generate adapters:
 *  1. delegate to [SchemaService] to build the catalog,
 *  2. delegate to a format-specific [SchemaResultRenderer],
 *  3. write the rendered output via [Console] and return an exit code.
 *
 * The schema command never fails on its own: the catalog is always
 * derivable from the embedded registry. Exit code is `0` unless a downstream
 * collaborator throws (which would propagate up to `Main` and exit `2`).
 */
class SchemaCommand(
    private val service: SchemaService,
    private val jsonRenderer: SchemaResultRenderer,
    private val humanRenderer: SchemaResultRenderer,
    private val console: Console,
) {

    /** Builds the catalog and emits it in the requested format. */
    fun run(format: SchemaFormat): Int {
        val catalog = service.catalog()
        val rendered = when (format) {
            SchemaFormat.JSON -> jsonRenderer.render(catalog)
            SchemaFormat.HUMAN -> humanRenderer.render(catalog)
        }
        console.writeLine(rendered)
        return 0
    }
}

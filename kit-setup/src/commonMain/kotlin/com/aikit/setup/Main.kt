package com.aikit.setup

import com.aikit.setup.io.FileSystem
import com.aikit.setup.output.StdConsole
import kotlin.system.exitProcess

/**
 * Shared entry point invoked by every native target's `main` (one per
 * `Platform.kt`). Kept deliberately thin: construct the [KitSetupApp] with
 * the host's [FileSystem], run it, and translate the returned exit code to
 * a process termination.
 *
 * All wiring lives in [KitSetupApp] so this function never needs to grow
 * past a handful of lines.
 */
fun runSetup(args: Array<String>, fs: FileSystem) {
    val app = KitSetupApp(files = fs, console = StdConsole())
    exitProcess(app.run(args))
}

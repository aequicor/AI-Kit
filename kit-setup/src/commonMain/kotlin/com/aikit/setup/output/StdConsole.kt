package com.aikit.setup.output

/**
 * Default [Console] backed by the platform's standard output via
 * [kotlin.io.println]. The sole production implementation; tests substitute
 * a recording fake.
 */
class StdConsole : Console {
    override fun writeLine(line: String) {
        println(line)
    }
}

package com.aikit.setup.output

/**
 * Single-line output sink used by command adapters.
 *
 * Introduced so commands don't call `println` directly — that lets tests
 * capture output deterministically and gives us a single place to swap the
 * destination later (e.g. routing usage errors to stderr) without rewriting
 * call sites.
 */
interface Console {

    /** Writes [line] followed by a newline. */
    fun writeLine(line: String)
}

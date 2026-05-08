package com.aikit.setup.manifest

/**
 * Parses a YAML document into a [RawNode] tree.
 *
 * The interface is the seam between this codebase and whatever YAML library
 * is selected for Kotlin/Native. Implementations are expected to throw on
 * malformed input; the loader catches and reports those failures uniformly.
 */
interface YamlParser {

    /**
     * Parses [content] (full document) and returns its root node. Throws on
     * any structural error — caller wraps the throwable into a load failure.
     */
    fun parse(content: String): RawNode
}

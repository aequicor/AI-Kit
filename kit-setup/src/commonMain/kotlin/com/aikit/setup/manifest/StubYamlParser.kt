package com.aikit.setup.manifest

/**
 * Placeholder [YamlParser] used by the skeleton until a real
 * Kotlin/Native-compatible YAML implementation is wired in. Throws on every
 * call so any path that depends on a parsed manifest fails loudly rather than
 * silently degrading.
 */
class StubYamlParser : YamlParser {

    override fun parse(content: String): RawNode {
        throw NotImplementedError(
            "YAML parsing is not wired up yet. Provide a Kotlin/Native YAML parser " +
                "implementation of YamlParser before invoking verify/generate.",
        )
    }
}

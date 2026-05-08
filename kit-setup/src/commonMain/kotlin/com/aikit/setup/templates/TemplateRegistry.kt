package com.aikit.setup.templates

/**
 * Read-only access to the bundled template tree (the binary's
 * `kit-setup/templates/` directory at build time).
 *
 * The CLI is Kotlin/Native — there is no JVM-style classpath resource lookup.
 * Templates are embedded as a generated source file at build time
 * (`Templates.kt`) and exposed through this interface so callers can be tested
 * with an in-memory map.
 */
interface TemplateRegistry {

    /** Returns the contents of the template at [path], or `null` if absent. */
    fun read(path: String): String?

    /**
     * Returns every template path whose key starts with [prefix] (no
     * trailing-slash normalization). Order is implementation-defined.
     */
    fun list(prefix: String): List<String>
}

/**
 * Default [TemplateRegistry] backed by the build-time–generated
 * [Templates.files] map.
 */
class EmbeddedTemplateRegistry : TemplateRegistry {

    override fun read(path: String): String? = Templates.files[path]

    override fun list(prefix: String): List<String> =
        Templates.files.keys.filter { it.startsWith(prefix) }
}

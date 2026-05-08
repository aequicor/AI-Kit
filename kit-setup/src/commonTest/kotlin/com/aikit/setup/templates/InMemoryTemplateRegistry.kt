package com.aikit.setup.templates

/**
 * Test-only [TemplateRegistry] backed by a plain map. Lets tests construct
 * the exact template tree they want without touching the build-time
 * generated [Templates] object — so we can assert behavior independent of
 * the bundled `kit-setup/templates/` files.
 */
class InMemoryTemplateRegistry(
    private val files: Map<String, String>,
) : TemplateRegistry {
    override fun read(path: String): String? = files[path]
    override fun list(prefix: String): List<String> = files.keys.filter { it.startsWith(prefix) }
}

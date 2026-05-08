package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/** ui.colors[].hex must match `^#[0-9A-Fa-f]{6}$`. */
class ColorHexRule : ValidationRule {
    private val pattern = Regex("""^#[0-9A-Fa-f]{6}$""")
    override fun check(manifest: Manifest): List<ValidationError> {
        val colors = RawAccess.sequence(RawAccess.child(RawAccess.child(manifest.raw, "ui"), "colors"))
            ?: return emptyList()
        val errors = mutableListOf<ValidationError>()
        colors.items.forEachIndexed { i, item ->
            val hex = RawAccess.scalar(RawAccess.child(item, "hex")) ?: return@forEachIndexed
            if (!pattern.matches(hex)) {
                errors += ValidationError(
                    path = "/ui/colors/$i/hex",
                    code = "color_hex_invalid",
                    message = "ui.colors[$i].hex='$hex' must match #RRGGBB.",
                )
            }
        }
        return errors
    }
}

/** lsp.extensions[i] and formatter.extensions[i] must start with `.`. */
class ExtensionFormatRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        listOf("/lsp/extensions" to RawAccess.child(RawAccess.child(manifest.raw, "lsp"), "extensions"),
            "/formatter/extensions" to RawAccess.child(RawAccess.child(manifest.raw, "formatter"), "extensions"))
            .forEach { (path, node) ->
                val seq = RawAccess.sequence(node) ?: return@forEach
                seq.items.forEachIndexed { i, item ->
                    val v = RawAccess.scalar(item) ?: return@forEachIndexed
                    if (!v.startsWith(".")) {
                        errors += ValidationError(
                            path = "$path/$i",
                            code = "extension_invalid",
                            message = "$path[$i]='$v' must start with '.' (e.g. '.kt').",
                        )
                    }
                }
            }
        return errors
    }
}

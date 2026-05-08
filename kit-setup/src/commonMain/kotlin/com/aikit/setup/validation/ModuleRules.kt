package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * Each module must carry the four schema-required fields and gradle_module,
 * if set, must start with `:` (Gradle path convention).
 */
class ModuleRequiredFieldsRule : ValidationRule {
    private val required = listOf("name", "source_root", "test_root", "docs_path")
    override fun check(manifest: Manifest): List<ValidationError> {
        val seq = RawAccess.sequence(RawAccess.child(manifest.raw, "modules"))
            ?: return listOf(
                ValidationError(
                    path = "/modules",
                    code = "required_field_missing",
                    message = "modules is required (a list of module entries).",
                ),
            )
        val errors = mutableListOf<ValidationError>()
        seq.items.forEachIndexed { i, item ->
            val mapping = RawAccess.mapping(item)
            if (mapping == null) {
                errors += ValidationError(
                    path = "/modules/$i",
                    code = "type_invalid",
                    message = "modules[$i] must be a mapping.",
                )
                return@forEachIndexed
            }
            for (key in required) {
                val v = RawAccess.scalar(RawAccess.child(item, key))
                if (v.isNullOrBlank()) {
                    errors += ValidationError(
                        path = "/modules/$i/$key",
                        code = "required_field_missing",
                        message = "modules[$i].$key is required.",
                    )
                }
            }
            val gradle = RawAccess.child(item, "gradle_module")
            val gradleScalar = RawAccess.scalar(gradle)
            if (gradleScalar != null && gradleScalar != "null" && !gradleScalar.startsWith(":")) {
                errors += ValidationError(
                    path = "/modules/$i/gradle_module",
                    code = "module_gradle_path_invalid",
                    message = "modules[$i].gradle_module must start with ':' (Gradle module path).",
                )
            }
        }
        return errors
    }
}

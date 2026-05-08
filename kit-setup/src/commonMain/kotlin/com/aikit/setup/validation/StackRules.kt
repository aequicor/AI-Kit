package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/** stack.language must be one of the schema-listed languages. */
class StackLanguageRule : ValidationRule {
    private val allowed = setOf("kotlin", "python", "typescript", "go", "rust", "java", "generic")
    override fun check(manifest: Manifest): List<ValidationError> {
        val stack = RawAccess.child(manifest.raw, "stack")
        if (!RawAccess.isPresent(stack)) {
            return listOf(
                ValidationError(
                    path = "/stack",
                    code = "required_field_missing",
                    message = "stack is required.",
                ),
            )
        }
        val language = RawAccess.scalar(RawAccess.child(stack, "language"))
            ?: return listOf(
                ValidationError(
                    path = "/stack/language",
                    code = "required_field_missing",
                    message = "stack.language is required.",
                ),
            )
        return if (language !in allowed) listOf(
            ValidationError(
                path = "/stack/language",
                code = "language_invalid",
                message = "stack.language='$language' is not one of: ${allowed.sorted().joinToString(", ")}.",
            ),
        ) else emptyList()
    }
}

/** stack.{build,compile,lint,test}_command are all required (minLength 1). */
class StackCommandsRule : ValidationRule {
    private val keys = listOf("build_command", "compile_command", "lint_command", "test_command")
    override fun check(manifest: Manifest): List<ValidationError> {
        val stack = RawAccess.child(manifest.raw, "stack") ?: return emptyList()
        return keys.mapNotNull { key ->
            val value = RawAccess.scalar(RawAccess.child(stack, key))
            if (value.isNullOrBlank()) {
                ValidationError(
                    path = "/stack/$key",
                    code = "required_field_missing",
                    message = "stack.$key is required and must not be empty.",
                )
            } else null
        }
    }
}

/** stack.profiles must list at least one profile name. */
class StackProfilesRule : ValidationRule {
    override fun check(manifest: Manifest): List<ValidationError> {
        val stack = RawAccess.child(manifest.raw, "stack") ?: return emptyList()
        val profilesNode = RawAccess.child(stack, "profiles")
        val seq = RawAccess.sequence(profilesNode)
        return when {
            seq == null -> listOf(
                ValidationError(
                    path = "/stack/profiles",
                    code = "required_field_missing",
                    message = "stack.profiles is required (a list of profile names).",
                ),
            )
            seq.items.isEmpty() -> listOf(
                ValidationError(
                    path = "/stack/profiles",
                    code = "stack_profiles_empty",
                    message = "stack.profiles must contain at least one profile name.",
                ),
            )
            else -> {
                val seen = mutableSetOf<String>()
                seq.items.mapIndexedNotNull { i, item ->
                    val v = RawAccess.scalar(item) ?: return@mapIndexedNotNull ValidationError(
                        path = "/stack/profiles/$i",
                        code = "type_invalid",
                        message = "stack.profiles[$i] must be a string.",
                    )
                    if (!seen.add(v)) ValidationError(
                        path = "/stack/profiles/$i",
                        code = "stack_profiles_duplicate",
                        message = "stack.profiles[$i]='$v' is duplicated; entries must be unique.",
                    ) else null
                }
            }
        }
    }
}

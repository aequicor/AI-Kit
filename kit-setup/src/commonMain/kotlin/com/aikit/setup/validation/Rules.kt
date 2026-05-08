package com.aikit.setup.validation

/**
 * Default rule set applied by both `verify` and the pre-flight step of
 * `generate`. Each rule encodes one schema constraint with its own stable
 * `code`. Add new rules by appending to the list — the runner is closed
 * for modification (OCP).
 */
fun defaultRules(): List<ValidationRule> = listOf(
    RootIsMappingRule(),
    KitVersionRule(),
    LanguageCodeRule(),
    ProjectNameRule(),
    HostsListRule(),
    OpencodeRequiresProviderAndModelsRule(),
    ClaudeCodeRequiresModelsRule(),
    StackLanguageRule(),
    StackCommandsRule(),
    StackProfilesRule(),
    ModuleRequiredFieldsRule(),
    ProviderRequiredFieldsRule(),
    ModelsRequiredFieldsRule(),
    ClaudeCodeModelsRequiredFieldsRule(),
    ApiKeyEnvLooksLikeLiteralRule(),
    ColorHexRule(),
    ExtensionFormatRule(),
)

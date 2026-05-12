package com.aikit.setup.validation

import com.aikit.setup.generation.PackageLoader
import com.aikit.setup.validation.rules.AgentPromptPresentRule
import com.aikit.setup.validation.rules.ManifestVersionRule
import com.aikit.setup.validation.rules.ModelProviderExistsRule
import com.aikit.setup.validation.rules.OrchestratorUnicityRule
import com.aikit.setup.validation.rules.ProjectSlugRule
import com.aikit.setup.validation.rules.ProviderAuthRule
import com.aikit.setup.validation.rules.RenderTargetsExistRule
import com.aikit.setup.validation.rules.RequiredTopLevelKeysRule
import com.aikit.setup.validation.rules.ResolvableModelsRule
import com.aikit.setup.validation.rules.TargetCollisionRule
import com.aikit.setup.validation.rules.TargetProviderExistsRule
import com.aikit.setup.validation.rules.UniqueIdsRule

/**
 * Default rule set applied by both `verify` and the pre-flight step of
 * `generate`. New constraints land as their own [ValidationRule] subclass
 * (one per file under [com.aikit.setup.validation.rules]) and get appended
 * here — the runner is open for extension, closed for modification.
 *
 * The [packages] dependency is passed through to rules that need to inspect
 * adapter/dialect packages (currently only [ResolvableModelsRule]). Pure
 * structural rules ignore it.
 */
fun defaultRules(packages: PackageLoader): List<ValidationRule> = listOf(
    RequiredTopLevelKeysRule(),
    ManifestVersionRule(),
    ProjectSlugRule(),
    UniqueIdsRule(),
    RenderTargetsExistRule(),
    ModelProviderExistsRule(),
    TargetProviderExistsRule(),
    ProviderAuthRule(),
    AgentPromptPresentRule(),
    OrchestratorUnicityRule(),
    ResolvableModelsRule(packages),
    TargetCollisionRule(packages),
)

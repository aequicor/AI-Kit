package com.aikit.setup.generation

import com.aikit.setup.model.Agent
import com.aikit.setup.model.CostHint
import com.aikit.setup.model.Model
import com.aikit.setup.model.ModelConstraint
import com.aikit.setup.model.ModelSelection
import com.aikit.setup.model.ModelTier
import com.aikit.setup.model.Module
import com.aikit.setup.model.Policies
import com.aikit.setup.model.Project
import com.aikit.setup.model.PromptSpec
import com.aikit.setup.model.Provider
import com.aikit.setup.model.Stack
import com.aikit.setup.model.Target
import com.aikit.setup.model.TaskTypeRule
import com.aikit.setup.model.TypedManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelResolverTest {

    private val anthropic = Provider("anthropic", "anthropic", null, "ANTHROPIC_API_KEY", null, null)
    private val openai = Provider("openai", "openai", null, "OPENAI_API_KEY", null, null)

    private val opus = Model(
        id = "opus", provider = "anthropic", model = "claude-opus-4-7", family = "anthropic",
        tier = ModelTier.REASONER, capabilities = listOf("reasoning", "code"),
        params = emptyMap(), costHint = CostHint.PREMIUM, priority = 10, contextWindow = null,
    )
    private val sonnet = Model(
        id = "sonnet", provider = "anthropic", model = "claude-sonnet-4-6", family = "anthropic",
        tier = ModelTier.BALANCED, capabilities = listOf("code"),
        params = emptyMap(), costHint = CostHint.BALANCED, priority = 10, contextWindow = null,
    )
    private val haiku = Model(
        id = "haiku", provider = "anthropic", model = "claude-haiku-4-5", family = "anthropic",
        tier = ModelTier.FAST, capabilities = emptyList(),
        params = emptyMap(), costHint = CostHint.CHEAP, priority = 10, contextWindow = null,
    )
    private val gpt5 = Model(
        id = "gpt5", provider = "openai", model = "gpt-5", family = "openai",
        tier = ModelTier.REASONER, capabilities = listOf("reasoning", "code"),
        params = emptyMap(), costHint = CostHint.PREMIUM, priority = 5, contextWindow = null,
    )

    private val claudeCode = Target(
        id = "claude-code", nativeProvider = "anthropic", canUseVia = emptyList(), adapter = null,
    )
    private val openTarget = Target(
        id = "open", nativeProvider = "any", canUseVia = listOf("anthropic", "openai"), adapter = null,
    )

    private fun manifestOf(
        models: List<Model> = listOf(opus, sonnet, haiku),
        providers: List<Provider> = listOf(anthropic, openai),
        targets: List<Target> = listOf(claudeCode),
        taskTypes: List<TaskTypeRule> = emptyList(),
        modelConstraints: Map<String, ModelConstraint> = emptyMap(),
    ): TypedManifest = TypedManifest(
        manifestVersion = "1.0.0",
        kitVersion = null,
        languageCode = "en",
        project = Project("Test", "test", null, null, emptyList()),
        stack = Stack(emptyList(), emptyList(), null, null, null, null, null, null),
        modules = emptyList<Module>(),
        targets = targets,
        renderTargets = emptyList(),
        providers = providers,
        models = models,
        taskTypes = taskTypes,
        promptDialects = emptyList(),
        targetAdapters = emptyList(),
        sharedPath = null,
        agents = emptyList(),
        tools = emptyList(),
        workflows = emptyList(),
        knowledge = null,
        policies = Policies(
            forbiddenPatterns = emptyList(),
            secretsDenyPatterns = emptyList(),
            testStrategy = null,
            sliceCaps = emptyMap(),
            modelConstraints = modelConstraints,
        ),
        ui = null,
    )

    private fun agent(
        id: String = "TestAgent",
        selection: ModelSelection = ModelSelection(),
        targetsOverride: List<String>? = null,
    ) = Agent(
        id = id,
        role = null,
        description = "",
        mode = null,
        modelSelection = selection,
        prompt = PromptSpec(defaultPath = "p.md", perFamily = emptyMap()),
        tools = emptyList(),
        permissions = emptyMap(),
        targetsOverride = targetsOverride,
    )

    @Test
    fun pinBypassesAllFiltering() {
        val resolver = ModelResolver(manifestOf())
        // haiku has no `reasoning` capability; needs would normally exclude it.
        val resolved = resolver.resolve(
            claudeCode,
            agent(selection = ModelSelection(needs = listOf("reasoning"), pin = "haiku")),
        )
        assertEquals("haiku", resolved?.id)
    }

    @Test
    fun preferredTierWinsAmongValidCandidates() {
        val resolver = ModelResolver(manifestOf())
        val resolved = resolver.resolve(
            claudeCode,
            agent(selection = ModelSelection(prefers = ModelTier.REASONER)),
        )
        assertEquals("opus", resolved?.id)
    }

    @Test
    fun balancedPreferenceLandsOnSonnet() {
        val resolver = ModelResolver(manifestOf())
        val resolved = resolver.resolve(
            claudeCode,
            agent(selection = ModelSelection(prefers = ModelTier.BALANCED, needs = listOf("code"))),
        )
        assertEquals("sonnet", resolved?.id)
    }

    @Test
    fun providerFilterRespectsTargetAllowList() {
        val resolver = ModelResolver(manifestOf(models = listOf(opus, gpt5), targets = listOf(claudeCode)))
        // gpt5 is openai-only; claude-code accepts only anthropic.
        val resolved = resolver.resolve(claudeCode, agent(selection = ModelSelection(prefers = ModelTier.REASONER)))
        assertEquals("opus", resolved?.id)
    }

    @Test
    fun anyProviderTargetAcceptsBothOpenaiAndAnthropic() {
        val resolver = ModelResolver(manifestOf(models = listOf(opus, gpt5), targets = listOf(openTarget)))
        val resolved = resolver.resolve(
            openTarget,
            agent(selection = ModelSelection(prefers = ModelTier.REASONER)),
        )
        // Both reasoner; tie broken by priority desc — opus has 10, gpt5 has 5.
        assertEquals("opus", resolved?.id)
    }

    @Test
    fun maxCostExcludesPremium() {
        val resolver = ModelResolver(manifestOf())
        val resolved = resolver.resolve(
            claudeCode,
            agent(selection = ModelSelection(maxCost = CostHint.BALANCED, needs = listOf("code"))),
        )
        assertEquals("sonnet", resolved?.id)
    }

    @Test
    fun strictReturnsNullWhenNoModelHasNeededCapability() {
        val resolver = ModelResolver(
            manifestOf(models = listOf(sonnet)), // sonnet has only [code], not [reasoning]
        )
        val ag = agent(selection = ModelSelection(needs = listOf("reasoning")))
        assertNull(resolver.resolveStrict(claudeCode, ag))
    }

    @Test
    fun fallbackResolveStillReturnsSomethingWhenStrictEmpty() {
        val resolver = ModelResolver(
            manifestOf(models = listOf(sonnet)),
        )
        val ag = agent(selection = ModelSelection(needs = listOf("reasoning")))
        // Strict pass empty → falls back to allowed pool.
        assertEquals("sonnet", resolver.resolve(claudeCode, ag)?.id)
    }

    @Test
    fun byTaskOverridesAgentBaseSelection() {
        val resolver = ModelResolver(manifestOf())
        val ag = agent(
            selection = ModelSelection(
                prefers = ModelTier.BALANCED,
                needs = listOf("code"),
                byTask = mapOf(
                    "debug" to ModelSelection(prefers = ModelTier.REASONER),
                ),
            ),
        )
        // Without task: balanced → sonnet.
        assertEquals("sonnet", resolver.resolve(claudeCode, ag)?.id)
        // With task=debug: reasoner override → opus.
        assertEquals("opus", resolver.resolve(claudeCode, ag, taskType = "debug")?.id)
    }

    @Test
    fun bySeverityBeatsByTask() {
        val resolver = ModelResolver(manifestOf())
        val ag = agent(
            selection = ModelSelection(
                prefers = ModelTier.BALANCED,
                needs = listOf("code"),
                byTask = mapOf("debug" to ModelSelection(prefers = ModelTier.REASONER, pin = "sonnet")),
                bySeverity = mapOf("critical" to ModelSelection(pin = "opus")),
            ),
        )
        assertEquals("opus", resolver.resolve(claudeCode, ag, taskType = "debug", severity = "critical")?.id)
    }

    @Test
    fun taskTypeDefaultsApplyWhenAgentLeavesPrefersUnset() {
        val resolver = ModelResolver(
            manifestOf(taskTypes = listOf(TaskTypeRule("planning", ModelTier.REASONER, null, listOf("reasoning")))),
        )
        val ag = agent(selection = ModelSelection())
        assertEquals("opus", resolver.resolve(claudeCode, ag, taskType = "planning")?.id)
    }

    @Test
    fun policiesModelConstraintsCanOnlyTighten() {
        val resolver = ModelResolver(
            manifestOf(
                modelConstraints = mapOf(
                    "planning" to ModelConstraint(minTier = ModelTier.REASONER, requireCapabilities = listOf("reasoning")),
                ),
            ),
        )
        val ag = agent(selection = ModelSelection(prefers = ModelTier.BALANCED))
        // Constraint floor raises min_tier above sonnet's BALANCED → opus.
        assertEquals("opus", resolver.resolveStrict(claudeCode, ag, taskType = "planning")?.id)
        // Without that task, no floor — fastest matching wins.
        assertEquals("sonnet", resolver.resolve(claudeCode, ag)?.id)
    }

    @Test
    fun pinPointingAtUndeclaredModelReturnsNull() {
        val resolver = ModelResolver(manifestOf())
        val ag = agent(selection = ModelSelection(pin = "non-existent"))
        assertNull(resolver.resolve(claudeCode, ag))
    }
}

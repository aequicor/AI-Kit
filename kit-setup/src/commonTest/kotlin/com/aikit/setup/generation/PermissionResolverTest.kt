package com.aikit.setup.generation

import com.aikit.setup.model.Module
import com.aikit.setup.model.Policies
import com.aikit.setup.model.Project
import com.aikit.setup.model.Stack
import com.aikit.setup.model.ToolEntry
import com.aikit.setup.model.TypedManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionResolverTest {

    private fun manifestOf(
        stack: Stack = Stack(emptyList(), emptyList(), null, null, null, null, null, null),
        tools: List<ToolEntry> = emptyList(),
        permissionsAllow: List<String> = emptyList(),
        permissionsDeny: List<String> = emptyList(),
    ): TypedManifest = TypedManifest(
        manifestVersion = "1.0.0",
        kitVersion = null,
        languageCode = "en",
        project = Project("Test", "test", null, null, emptyList()),
        stack = stack,
        modules = emptyList<Module>(),
        targets = emptyList(),
        renderTargets = emptyList(),
        providers = emptyList(),
        models = emptyList(),
        taskTypes = emptyList(),
        promptDialects = emptyList(),
        targetAdapters = emptyList(),
        sharedPath = null,
        agents = emptyList(),
        tools = tools,
        workflows = emptyList(),
        knowledge = null,
        policies = Policies(
            forbiddenPatterns = emptyList(),
            secretsDenyPatterns = emptyList(),
            testStrategy = null,
            sliceCaps = emptyMap(),
            permissionsAllow = permissionsAllow,
            permissionsDeny = permissionsDeny,
        ),
        ui = null,
    )

    @Test
    fun kitPipelineEssentialsAlwaysPresent() {
        val resolved = PermissionResolver(manifestOf()).resolve()
        // Side-effect tools the prompt asks the agent to use must not
        // prompt the user on every call.
        assertTrue("ExitPlanMode" in resolved.allow)
        assertTrue("TodoWrite" in resolved.allow)
        assertTrue("Skill" in resolved.allow)
        // Git verbs the pipeline body actually issues.
        assertTrue("Bash(git status:*)" in resolved.allow)
        assertTrue("Bash(git commit:*)" in resolved.allow)
        assertTrue("Bash(git push:*)" in resolved.allow)
        // Hard deny on bare --force; --force-with-lease slips through because
        // the deny pattern requires --force as the exact token.
        assertTrue("Bash(git push --force *)" in resolved.deny)
        assertTrue("Bash(git push --force)" in resolved.deny)
    }

    @Test
    fun uxPrimitivesAreNotInDefaultAllow() {
        // Reason: `AskUserQuestion` and `EnterPlanMode` are auto-allowed UX/AWAIT
        // primitives in current Claude Code and the settings.json schema regex
        // does not recognise them — emitting them in `permissions.allow` makes
        // the whole file fail schema validation. They must not appear unless
        // the manifest author opted in via `policies.permissions.allow`.
        val resolved = PermissionResolver(manifestOf()).resolve()
        assertFalse("AskUserQuestion" in resolved.allow)
        assertFalse("EnterPlanMode" in resolved.allow)
    }

    @Test
    fun stackCommandsContributeBashPrefixes() {
        val resolved = PermissionResolver(
            manifestOf(
                stack = Stack(
                    languages = listOf("kotlin"),
                    frameworks = emptyList(),
                    buildCommand = "./gradlew",
                    compileCommand = "./gradlew compileKotlin",
                    lintCommand = "./gradlew detekt ktlintCheck",
                    testCommand = "./gradlew :app:test",
                    formatCommand = null,
                    runCommand = null,
                ),
            ),
        ).resolve()
        // Every command's first token becomes its own Bash(...) entry,
        // deduped if the same token appears twice (here ./gradlew).
        assertTrue("Bash(./gradlew:*)" in resolved.allow)
        assertEquals(1, resolved.allow.count { it == "Bash(./gradlew:*)" })
    }

    @Test
    fun differentBuildToolsAddDifferentPatterns() {
        val resolved = PermissionResolver(
            manifestOf(
                stack = Stack(
                    languages = listOf("typescript"),
                    frameworks = emptyList(),
                    buildCommand = null,
                    compileCommand = "tsc -b",
                    lintCommand = "eslint .",
                    testCommand = "pnpm test",
                    formatCommand = "prettier --write",
                    runCommand = null,
                ),
            ),
        ).resolve()
        assertTrue("Bash(tsc:*)" in resolved.allow)
        assertTrue("Bash(eslint:*)" in resolved.allow)
        assertTrue("Bash(pnpm:*)" in resolved.allow)
        assertTrue("Bash(prettier:*)" in resolved.allow)
    }

    @Test
    fun mcpToolsGetWildcardPatterns() {
        val resolved = PermissionResolver(
            manifestOf(
                tools = listOf(
                    ToolEntry("serena", "mcp-stdio", "serena-mcp", emptyList(), null, null, enabled = true),
                    ToolEntry("playwright", "mcp-stdio", "playwright-mcp", emptyList(), null, null, enabled = true),
                    ToolEntry("disabled", "mcp-stdio", "x", emptyList(), null, null, enabled = false),
                    ToolEntry("lsp-kotlin", "lsp", "kotlin-lsp", emptyList(), null, null, enabled = true),
                ),
            ),
        ).resolve()
        assertTrue("mcp__serena__*" in resolved.allow)
        assertTrue("mcp__playwright__*" in resolved.allow)
        // Disabled tools and non-MCP kinds (lsp) are skipped.
        assertFalse("mcp__disabled__*" in resolved.allow)
        assertFalse("mcp__lsp-kotlin__*" in resolved.allow)
    }

    @Test
    fun userManifestAllowAppends() {
        val resolved = PermissionResolver(
            manifestOf(permissionsAllow = listOf("Bash(docker:*)", "WebFetch(domain:internal.corp)")),
        ).resolve()
        assertTrue("Bash(docker:*)" in resolved.allow)
        assertTrue("WebFetch(domain:internal.corp)" in resolved.allow)
        // Kit essentials still present.
        assertTrue("ExitPlanMode" in resolved.allow)
    }

    @Test
    fun userManifestDenyWinsOverAllow() {
        // The user wants to forbid git reset --hard even though the kit
        // pipeline allows it. Deny entries are pruned from the allow list.
        val resolved = PermissionResolver(
            manifestOf(permissionsDeny = listOf("Bash(git reset --hard:*)")),
        ).resolve()
        assertTrue("Bash(git reset --hard:*)" in resolved.deny)
        assertFalse("Bash(git reset --hard:*)" in resolved.allow)
        // Other git verbs unaffected.
        assertTrue("Bash(git commit:*)" in resolved.allow)
    }

    @Test
    fun claudeCodeAllowJsonShape() {
        val json = PermissionResolver(manifestOf()).claudeCodeAllowJson()
        // Compact JSON array of strings, no whitespace.
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"ExitPlanMode\""))
        assertTrue(json.contains("\"Bash(git status:*)\""))
        // Schema-rejected UX primitives must NOT appear in the default output.
        assertFalse(json.contains("\"AskUserQuestion\""))
        assertFalse(json.contains("\"EnterPlanMode\""))
    }

    @Test
    fun opencodePermissionJsonTranslatesEachCategory() {
        val json = PermissionResolver(
            manifestOf(
                stack = Stack(
                    languages = listOf("typescript"),
                    frameworks = emptyList(),
                    buildCommand = null,
                    compileCommand = null,
                    lintCommand = null,
                    testCommand = "pnpm test",
                    formatCommand = null,
                    runCommand = null,
                ),
                // AskUserQuestion is no longer in the default allow list (it
                // breaks Claude Code's schema); add it explicitly so we still
                // exercise the AskUserQuestion → question translation branch.
                permissionsAllow = listOf("AskUserQuestion"),
            ),
        ).opencodePermissionJson()
        // Picker → question: "allow"
        assertTrue(json.contains("\"question\":\"allow\""))
        // Todo-list → todowrite: "allow"
        assertTrue(json.contains("\"todowrite\":\"allow\""))
        // File ops → read / edit: "allow"
        assertTrue(json.contains("\"read\":\"allow\""))
        assertTrue(json.contains("\"edit\":\"allow\""))
        // Bash patterns → bash: { "*": "ask", "git status *": "allow", ... }
        assertTrue(json.contains("\"bash\":{"))
        assertTrue(json.contains("\"git status *\":\"allow\""))
        assertTrue(json.contains("\"pnpm *\":\"allow\""))
        // OpenCode evaluates last-match-wins → `*` must come FIRST as the
        // floor, with specific allow/deny patterns AFTER so they override.
        val bashStart = json.indexOf("\"bash\":{")
        val starIdx = json.indexOf("\"*\":\"ask\"", bashStart)
        val gitIdx = json.indexOf("\"git status *\":\"allow\"", bashStart)
        assertTrue(starIdx in 0..gitIdx, "Catch-all `*` must precede specific bash patterns")
    }

    @Test
    fun opencodeDenyEmitsBashDenyEntry() {
        val json = PermissionResolver(
            manifestOf(permissionsDeny = listOf("Bash(curl:*)")),
        ).opencodePermissionJson()
        assertTrue(json.contains("\"curl *\":\"deny\""))
    }

    @Test
    fun emptyAllowDenyStillProducesNonTrivialOutput() {
        // Regression for the original bug: `permissions.allow: []` in the
        // generated settings.json rendered every tool call as a prompt. The
        // resolver's built-in kit-pipeline list closes that hole.
        val resolved = PermissionResolver(manifestOf()).resolve()
        assertTrue(resolved.allow.size >= 15)
    }
}

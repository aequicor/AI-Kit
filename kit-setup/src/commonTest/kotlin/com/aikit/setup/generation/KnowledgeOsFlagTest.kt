package com.aikit.setup.generation

import com.aikit.setup.model.Policies
import com.aikit.setup.model.Project
import com.aikit.setup.model.Stack
import com.aikit.setup.model.ToolEntry
import com.aikit.setup.model.TypedManifest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the contract behind the KNOWLEDGE_OS_ENABLED placeholder flag. The id
 * `knowledge-os` is a well-known opt-in trigger; a test failure here is a
 * breaking change to the agent integration and to every memory snippet that
 * gates on the flag.
 */
class KnowledgeOsFlagTest {

    private fun manifestWith(tools: List<ToolEntry>): TypedManifest = TypedManifest(
        manifestVersion = "1.0.0",
        kitVersion = null,
        languageCode = "en",
        project = Project("Test", "test", null, null, emptyList()),
        stack = Stack(emptyList(), emptyList(), null, null, null, null, null, null),
        modules = emptyList(),
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
            modelConstraints = emptyMap(),
        ),
        ui = null,
    )

    private fun tool(
        id: String = "knowledge-os",
        kind: String = "mcp-http",
        enabled: Boolean = true,
        url: String? = "http://example/mcp",
    ) = ToolEntry(
        id = id,
        kind = kind,
        command = null,
        args = emptyList(),
        url = url,
        apiKeyEnv = null,
        enabled = enabled,
    )

    @Test
    fun mcpHttpKnowledgeOsEnablesFlag() {
        assertTrue(isKnowledgeOsEnabled(manifestWith(listOf(tool()))))
    }

    @Test
    fun mcpStdioKnowledgeOsEnablesFlag() {
        assertTrue(
            isKnowledgeOsEnabled(
                manifestWith(listOf(tool(kind = "mcp-stdio", url = null))),
            ),
        )
    }

    @Test
    fun missingFromToolsLeavesFlagOff() {
        assertFalse(isKnowledgeOsEnabled(manifestWith(emptyList())))
    }

    @Test
    fun disabledEntryLeavesFlagOff() {
        // Reason: enabled=false is the explicit "wired but parked" state — agents
        // must not try MCP calls when the user has paused the integration.
        assertFalse(isKnowledgeOsEnabled(manifestWith(listOf(tool(enabled = false)))))
    }

    @Test
    fun nonMcpKindLeavesFlagOff() {
        // Reason: an LSP / builtin / http (non-mcp) entry under id `knowledge-os`
        // is a misconfiguration — the flag gates MCP-tool calls, which only make
        // sense over an mcp-* transport.
        assertFalse(
            isKnowledgeOsEnabled(manifestWith(listOf(tool(kind = "builtin", url = null)))),
        )
    }

    @Test
    fun foreignIdDoesNotEnableFlag() {
        // Reason: only the well-known id `knowledge-os` flips the flag. A custom
        // MCP knowledge backend with a different id stays in the generic mcp-*
        // path and snippets fall back to the filesystem branch.
        assertFalse(
            isKnowledgeOsEnabled(manifestWith(listOf(tool(id = "knowledge-mcp")))),
        )
    }
}

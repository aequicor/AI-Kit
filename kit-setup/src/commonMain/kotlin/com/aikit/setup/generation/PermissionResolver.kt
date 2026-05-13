package com.aikit.setup.generation

import com.aikit.setup.model.TypedManifest
import com.aikit.setup.output.Json

/**
 * Resolves the runtime tool-permission allow/deny set for a kit.
 *
 * Input layers (merged in order, deduplicated, later wins on conflict):
 *  1. **Kit pipeline essentials** — git verbs `/kit`, `/kit-do`, `/kit-fix` invoke,
 *     plus the native side-effect tools the prompt asks the agent to use
 *     (`TodoWrite`, `ExitPlanMode`, `Skill`, `Agent`, file ops, …). Tools that
 *     are UX/AWAIT mechanisms rather than side-effect calls — `AskUserQuestion`
 *     and `EnterPlanMode` — are deliberately **not** listed. Current Claude Code
 *     auto-allows them and its settings.json schema actively rejects them in
 *     `permissions.allow` (their names are absent from the recognised tool
 *     regex), so emitting them used to make every generated settings.json fail
 *     schema validation.
 *  2. **Stack-derived patterns** — first token of each `stack.{compile,test,lint,
 *     format,run,build}_command` becomes a `Bash(<token>:*)` allow pattern, so a
 *     Kotlin project gets `Bash(./gradlew:*)`, a TS project `Bash(npm:*)`, etc.
 *  3. **MCP server tools** — each enabled `tools[]` entry of an `mcp-*` kind
 *     becomes `mcp__<id>__*`.
 *  4. **User-declared `policies.permissions.allow`** — additional patterns the
 *     manifest author wants always-allowed.
 *  5. **User-declared `policies.permissions.deny`** — overrides any allow above.
 *
 * Output is the canonical Claude Code grammar (used as-is by Claude Code and
 * Qwen Code, since Qwen Code adopts the Gemini-CLI–style format that is
 * compatible). For OpenCode, [opencodePermissionJson] translates into its
 * nested object form (`{ bash: { "git *": "allow" }, question: "allow", … }`).
 *
 * Adapters that don't expose a permission model (Cursor, Aider) ignore the
 * resolver entirely — they have no settings slot to fill.
 */
internal class PermissionResolver(private val manifest: TypedManifest) {

    /** Pure-data view of the merged allow/deny set in Claude Code grammar. */
    data class Resolved(val allow: List<String>, val deny: List<String>)

    fun resolve(): Resolved {
        val allow = linkedSetOf<String>()
        allow += KIT_PIPELINE_ALLOW
        allow += stackAllowPatterns()
        allow += mcpAllowPatterns()
        allow += manifest.policies.permissionsAllow

        val deny = linkedSetOf<String>()
        deny += KIT_PIPELINE_DENY
        deny += manifest.policies.permissionsDeny

        // Deny wins: a manifest deny rule removes the same string from allow.
        // Pattern overlap (e.g. allow `Bash(git push:*)` + deny `Bash(git push --force *)`)
        // is resolved by the runner itself per its grammar — we don't try to
        // outsmart it here.
        allow.removeAll(deny)

        return Resolved(allow.toList(), deny.toList())
    }

    /** Claude Code / Qwen Code: JSON array of strings. */
    fun claudeCodeAllowJson(): String = Json.encode(resolve().allow)
    fun claudeCodeDenyJson(): String = Json.encode(resolve().deny)

    /**
     * OpenCode: nested object. Each top-level key is an OpenCode tool category
     * (`bash`, `edit`, `read`, `write`, `webfetch`, `websearch`, `task`,
     * `skill`, `question`, `todowrite`, …). A value is either a literal
     * `"allow"` / `"ask"` / `"deny"`, or a `{ "<pattern>": "allow", … }` map
     * with `"*"` as the catch-all.
     *
     * Translation rules (Claude Code → OpenCode):
     *  - `AskUserQuestion`               → `question: "allow"`
     *  - `TodoWrite`                     → `todowrite: "allow"`
     *  - `Read(...)` / bare `Read`       → `read: "allow"`
     *  - `Edit(...)` / `Write(...)`      → `edit: "allow"`
     *  - `Glob` / `Grep`                 → covered by OpenCode's default
     *                                       lsp/glob/grep allow; we emit
     *                                       `glob: "allow"` / `grep: "allow"`
     *                                       explicitly to be safe.
     *  - `Bash(prefix:*)` / `Bash(prefix *)` → `bash: { "<prefix> *": "allow" }`
     *  - bare `Bash`                     → `bash: "allow"` (override the default
     *                                       `bash: "ask"`)
     *  - `WebFetch(...)`                 → `webfetch: "allow"`
     *  - `mcp__server__tool` / `mcp__server__*` → kept as Claude-Code grammar
     *                                       inside an `mcp` list (OpenCode
     *                                       reads MCP tool ids the same way).
     *  - anything unrecognised           → passed through into an `extras` list
     *                                       so nothing is silently lost.
     */
    fun opencodePermissionJson(): String {
        val resolved = resolve()
        val obj = linkedMapOf<String, Any?>()

        val bashAllow = linkedMapOf<String, String>()
        val bashDeny = linkedMapOf<String, String>()
        val extras = mutableListOf<String>()
        var bareBashAllow = false

        for (rule in resolved.allow) {
            when {
                rule == "AskUserQuestion" -> obj["question"] = "allow"
                rule == "TodoWrite" -> obj["todowrite"] = "allow"
                rule == "Glob" -> obj["glob"] = "allow"
                rule == "Grep" -> obj["grep"] = "allow"
                rule == "WebSearch" -> obj["websearch"] = "allow"
                rule == "Bash" -> bareBashAllow = true
                rule.startsWith("Read") -> obj["read"] = "allow"
                rule.startsWith("Edit") || rule.startsWith("Write") || rule.startsWith("NotebookEdit") -> obj["edit"] = "allow"
                rule.startsWith("WebFetch") -> obj["webfetch"] = "allow"
                rule.startsWith("Bash(") -> {
                    val pattern = extractBashPattern(rule) ?: continue
                    bashAllow[pattern] = "allow"
                }
                rule.startsWith("Skill(") -> obj["skill"] = "allow"
                rule.startsWith("Agent(") -> obj["task"] = "allow"
                rule.startsWith("mcp__") -> extras += rule
                else -> extras += rule
            }
        }
        for (rule in resolved.deny) {
            when {
                rule.startsWith("Bash(") -> {
                    val pattern = extractBashPattern(rule) ?: continue
                    bashDeny[pattern] = "deny"
                }
                rule.startsWith("Read") -> obj["read"] = "deny"
                rule.startsWith("Edit") || rule.startsWith("Write") -> obj["edit"] = "deny"
                rule == "WebFetch" || rule.startsWith("WebFetch") -> obj["webfetch"] = "deny"
                else -> extras += "!$rule"
            }
        }

        // OpenCode evaluates bash rules **last-match-wins**, so the catch-all
        // `*` goes FIRST (as the floor) and the explicit allow/deny patterns
        // come after — the last one that matches a given command is the one
        // that decides. Putting `*` last would make every command match `*`
        // unconditionally and override every specific rule above it.
        if (bashAllow.isNotEmpty() || bashDeny.isNotEmpty() || bareBashAllow) {
            val bashMap = linkedMapOf<String, String>()
            bashMap["*"] = if (bareBashAllow) "allow" else "ask"
            bashMap.putAll(bashAllow)
            bashMap.putAll(bashDeny)
            obj["bash"] = bashMap
        }
        if (extras.isNotEmpty()) obj["extras"] = extras
        return Json.encode(obj)
    }

    private fun stackAllowPatterns(): List<String> {
        val out = linkedSetOf<String>()
        for (cmd in listOfNotNull(
            manifest.stack.buildCommand,
            manifest.stack.compileCommand,
            manifest.stack.lintCommand,
            manifest.stack.testCommand,
            manifest.stack.formatCommand,
            manifest.stack.runCommand,
        )) {
            val token = firstShellToken(cmd) ?: continue
            out += "Bash($token:*)"
        }
        return out.toList()
    }

    private fun mcpAllowPatterns(): List<String> {
        val out = linkedSetOf<String>()
        for (tool in manifest.tools) {
            if (!tool.enabled) continue
            if (!tool.kind.startsWith("mcp-")) continue
            out += "mcp__${tool.id}__*"
        }
        return out.toList()
    }

    /**
     * Pulls the bash pattern out of `Bash(...)` so the OpenCode form can
     * use it as the map key. Accepts both `Bash(prefix:*)` and `Bash(prefix *)`
     * — the OpenCode form is `"<prefix> *"`.
     */
    private fun extractBashPattern(rule: String): String? {
        val inner = rule.removePrefix("Bash(").removeSuffix(")")
        if (inner.isEmpty() || inner == rule) return null
        // Claude Code accepts both `prefix:*` and `prefix *` as equivalent; the
        // OpenCode form is the second one (space-delimited).
        val normalized = inner.replace(":*", " *")
        return normalized
    }

    /**
     * First whitespace-delimited token of a shell command. `./gradlew` →
     * `./gradlew`; `pnpm test` → `pnpm`. Commands using `cd X && Y` are not
     * supported — the manifest is expected to wrap those in a script.
     */
    private fun firstShellToken(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return null
        val end = trimmed.indexOfFirst { it == ' ' || it == '\t' }
        return if (end < 0) trimmed else trimmed.substring(0, end)
    }

    companion object {
        /**
         * Always-allowed entries. These cover every side-effect tool the
         * AI-Kit pipeline body issues, plus the shell commands invoked by
         * Session 2 / Session 3. Without them the agent gets prompted on every
         * step's `git commit` / `git show` / `Read`, which is the UX hole this
         * resolver exists to close.
         *
         * Deliberately **excluded**: `AskUserQuestion` and `EnterPlanMode`.
         * They are UX/AWAIT primitives (the very mechanism by which Claude
         * Code blocks for user input), auto-allowed at runtime, and rejected
         * by the current `.claude/settings.json` schema regex — listing them
         * here made every generated settings.json fail validation.
         */
        private val KIT_PIPELINE_ALLOW = listOf(
            // Native side-effect / orchestration tools the prompt body uses.
            "ExitPlanMode",
            "TodoWrite",
            "Skill",
            "Agent",
            "Read",
            "Edit",
            "Write",
            "Glob",
            "Grep",
            // git verbs the pipeline runs (Session 2 + Session 3).
            "Bash(git status:*)",
            "Bash(git log:*)",
            "Bash(git show:*)",
            "Bash(git diff:*)",
            "Bash(git add:*)",
            "Bash(git commit:*)",
            "Bash(git checkout:*)",
            "Bash(git cat-file:*)",
            "Bash(git rev-parse:*)",
            "Bash(git merge-base:*)",
            "Bash(git stash:*)",
            "Bash(git branch:*)",
            "Bash(git reset --soft:*)",
            "Bash(git reset --hard:*)",
            "Bash(git push:*)",
            // generic read-only inspection.
            "Bash(ls:*)",
            "Bash(cat:*)",
            "Bash(pwd)",
        )

        /**
         * Hard denies. Force-push and rm-of-root are the cliff edges; everything
         * else is steered by the prompt-level constitution (which the agent is
         * trained to follow). We leave `git push --force-with-lease` legal — the
         * grammar `Bash(git push --force *)` only matches when `--force` is the
         * exact token, so `--force-with-lease` slips through correctly.
         */
        private val KIT_PIPELINE_DENY = listOf(
            "Bash(git push --force *)",
            "Bash(git push --force)",
            "Bash(rm -rf /:*)",
        )
    }
}

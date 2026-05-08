package com.aikit.setup.generation

/**
 * Decides what to do with each entry in `kit/_index.txt`. Mirrors the table
 * in `docs/prompts/setup.md` PHASE 3.3 — plus the multi-host commands/skills
 * rule from PHASE 3.5 — so the binary's output layout matches the
 * prompt-driven workflow's layout.
 */
class TemplateClassifier {

    fun classify(entry: String, hosts: List<String>, vaultPath: String): List<TemplateAction> {
        if (!entry.startsWith("kit/")) return emptyList()
        val rel = entry.removePrefix("kit/")

        if (rel in META_FILES || rel in INTERNAL_FILES) return emptyList()
        if (rel == "nested/MODULE.body.md.template") return emptyList()

        // _shared/: most files render once per active host into <host_dir>/<rest>;
        // agent bodies and *.body.md.template files are INCLUDE-only (never written
        // directly).
        if (rel.startsWith("_shared/")) {
            val rest = rel.removePrefix("_shared/")
            if (rest.startsWith("agents/")) return emptyList()
            if (rest.endsWith(".body.md.template")) return emptyList()
            return hosts.map { host ->
                val hostDir = hostTemplateDir(host)
                TemplateAction(entry, host, "$hostDir/${rest.removeTemplateSuffix()}")
            }
        }

        if (rel == "AGENTS.md.template") {
            return if ("opencode" in hosts) listOf(TemplateAction(entry, "opencode", "AGENTS.md")) else emptyList()
        }
        if (rel == "CLAUDE.md.template") {
            return if ("claude-code" in hosts) listOf(TemplateAction(entry, "claude-code", "CLAUDE.md")) else emptyList()
        }
        if (rel == "opencode.json.template") {
            return if ("opencode" in hosts) listOf(TemplateAction(entry, "opencode", "opencode.json")) else emptyList()
        }
        if (rel == ".mcp.json.template") {
            return if ("claude-code" in hosts) {
                listOf(TemplateAction(entry, "claude-code", ".mcp.json", optional = true))
            } else emptyList()
        }
        if (rel == "AUTO_MEMORY.md.template") {
            return if (hosts.isNotEmpty()) listOf(TemplateAction(entry, hosts.first(), "AUTO_MEMORY.md")) else emptyList()
        }
        if (rel.startsWith(".opencode/")) {
            return if ("opencode" in hosts) {
                listOf(TemplateAction(entry, "opencode", rel.removeTemplateSuffix()))
            } else emptyList()
        }
        if (rel.startsWith(".claude/")) {
            return if ("claude-code" in hosts) {
                listOf(TemplateAction(entry, "claude-code", rel.removeTemplateSuffix()))
            } else emptyList()
        }
        if (rel.startsWith(".planning/")) {
            if (hosts.isEmpty()) return emptyList()
            // Per-task scaffold — agents copy and fill it in later, so leave
            // the placeholders intact and keep the `.template` suffix.
            if (rel == ".planning/tasks/TASK.md.template") {
                return listOf(TemplateAction(entry, hosts.first(), rel, verbatim = true))
            }
            return listOf(TemplateAction(entry, hosts.first(), rel.removeTemplateSuffix()))
        }
        if (rel.startsWith(".vault/")) {
            return if (hosts.isNotEmpty()) {
                val rewritten = rel.replaceFirst(".vault/", "$vaultPath/")
                listOf(TemplateAction(entry, hosts.first(), rewritten.removeTemplateSuffix()))
            } else emptyList()
        }
        return emptyList()
    }

    private fun hostTemplateDir(host: String): String = when (host) {
        "opencode" -> ".opencode"
        "claude-code" -> ".claude"
        else -> ".$host"
    }

    private fun String.removeTemplateSuffix(): String =
        if (endsWith(".template")) removeSuffix(".template") else this

    private companion object {
        val META_FILES = setOf("manifest.schema.json", "profile.schema.json")
        val INTERNAL_FILES = setOf("_index.txt")
    }
}

/**
 * One unit of generator work: render embedded source [sourcePath] using
 * the [host]'s substitution context and write the result to
 * `<target>/[targetRelPath]`. [optional] = true means it's fine to skip
 * writing if rendered content is empty (e.g. `.mcp.json` when no MCP
 * servers are enabled).
 */
data class TemplateAction(
    val sourcePath: String,
    val host: String,
    val targetRelPath: String,
    val optional: Boolean = false,
    /**
     * When `true`, the generator copies the file's bytes as-is — no INCLUDE
     * resolution and no `{{VAR}}` substitution. Used for scaffold templates
     * (e.g. `.planning/tasks/TASK.md.template`) that agents instantiate
     * per-task at runtime; leaving the placeholders untouched is intentional.
     */
    val verbatim: Boolean = false,
)

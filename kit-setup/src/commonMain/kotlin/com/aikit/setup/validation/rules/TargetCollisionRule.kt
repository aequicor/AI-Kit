package com.aikit.setup.validation.rules

import com.aikit.setup.generation.PackageLoader
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.model.ManifestModel
import com.aikit.setup.validation.ValidationError
import com.aikit.setup.validation.ValidationRule

/**
 * Catches render-target combinations that would clobber each other on disk.
 *
 * Two adapters can declare the same `instruction_file` (`AGENTS.md` is
 * shared by both `opencode` and `qwen-code`) or the same `settings_file`.
 * Generation runs targets in order, so the second target silently overwrites
 * the first. This rule reports the conflict at verify time so the manifest
 * author either drops one of the colliding targets from `render_targets[]`
 * or accepts the loss explicitly.
 *
 * Per-artifact paths (`.claude/agents/{id}.md` etc.) live in distinct
 * subtrees per adapter, so we don't enumerate them here — only the
 * static, runner-global files (`instruction_file`, `settings_file`) can
 * collide between adapters.
 */
class TargetCollisionRule(
    private val packages: PackageLoader,
) : ValidationRule {

    override fun check(manifest: Manifest): List<ValidationError> {
        val typed = ManifestModel.from(manifest)
        if (typed.renderTargets.size < 2) return emptyList()

        // path → list of (targetId, slot label)
        val owners = linkedMapOf<String, MutableList<Pair<String, String>>>()

        for (renderTargetId in typed.renderTargets) {
            val target = typed.targets.firstOrNull { it.id == renderTargetId } ?: continue
            val adapterId = target.adapter ?: target.id
            val pointer = typed.targetAdapters.firstOrNull { it.id == adapterId } ?: continue
            val adapter = try {
                packages.loadAdapter(pointer.path)
            } catch (e: Throwable) {
                continue
            }
            adapter.instructionFile?.takeIf { it.isNotEmpty() && !it.endsWith("/") }?.let {
                owners.getOrPut(it) { mutableListOf() } += renderTargetId to "instruction_file"
            }
            adapter.settingsFile?.takeIf { it.isNotEmpty() }?.let {
                owners.getOrPut(it) { mutableListOf() } += renderTargetId to "settings_file"
            }
        }

        val errors = mutableListOf<ValidationError>()
        for ((path, claimants) in owners) {
            if (claimants.size < 2) continue
            val ids = claimants.joinToString(", ") { (target, slot) -> "$target ($slot)" }
            errors += ValidationError(
                path = "/render_targets",
                code = "target_output_collision",
                message = "Multiple render targets write to the same path `$path`: $ids. " +
                    "The later target silently overwrites the earlier one.",
                hint = "Drop one of the colliding targets from `render_targets[]`, " +
                    "or split the kit into per-target roots and run generate twice.",
            )
        }
        return errors
    }
}

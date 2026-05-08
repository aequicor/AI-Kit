package com.aikit.setup.manifest.profile

import com.aikit.setup.manifest.DeepMerge
import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode
import com.aikit.setup.manifest.asStringList
import com.aikit.setup.manifest.path
import com.aikit.setup.validation.ValidationError

/**
 * Resolves the `stack.profiles[]` block of a manifest.
 *
 * Each name listed there is loaded from `templates/profiles/<axis>/<name>.yaml`,
 * checked against axis cardinality and field-whitelist rules, then merged in
 * canonical order (security-baseline first, then `language`, then frameworks,
 * then remaining capabilities) — and finally the user's base manifest is
 * layered on top, so explicit base values always override profile defaults.
 *
 * `stack.profiles` is consumed at this stage and removed from the resolved
 * manifest, so downstream layers (validation, typed model, generator) never
 * see it.
 *
 * The resolver does not load `extends:` (org-level base manifest layering) —
 * that's a future, separate slot.
 */
class ProfileResolver(
    private val loader: ProfileLoader,
) {

    sealed class Result {
        /** All profiles loaded and merged successfully. */
        data class Success(val manifest: Manifest, val resolved: List<Profile>) : Result()

        /**
         * One or more profiles failed to load, validate, or merge. Errors
         * collected into the same shape used by the regular validator so the
         * verify renderer treats them uniformly.
         */
        data class Failure(val errors: List<ValidationError>) : Result()
    }

    fun resolve(manifest: Manifest): Result {
        val root = manifest.raw as? RawNode.Mapping ?: return Result.Success(manifest, emptyList())
        val names = root.path("stack", "profiles").asStringList()
        if (names.isEmpty()) {
            // No profiles requested — strip the (possibly explicitly empty) list and return.
            return Result.Success(Manifest(stripStackProfiles(root)), emptyList())
        }

        // Detect duplicate names up front — duplicates would silently merge twice.
        val dupes = names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (dupes.isNotEmpty()) {
            return Result.Failure(
                dupes.map {
                    ValidationError(
                        path = "/stack/profiles",
                        code = "profile_duplicate",
                        message = "Profile `$it` listed more than once in stack.profiles.",
                    )
                },
            )
        }

        // Load every profile up-front, accumulating load errors so the user
        // sees them all in one verify pass instead of one-by-one.
        val loadErrors = mutableListOf<ValidationError>()
        val profiles = mutableListOf<Profile>()
        for (name in names) {
            when (val r = loader.load(name)) {
                is ProfileLoader.Result.Found -> profiles += r.profile
                is ProfileLoader.Result.Failure -> loadErrors += r.errors
            }
        }
        if (loadErrors.isNotEmpty()) return Result.Failure(loadErrors)

        // Cardinality across loaded profiles.
        val cardErrors = checkCardinality(profiles)
        if (cardErrors.isNotEmpty()) return Result.Failure(cardErrors)

        // Per-profile axis-field whitelist. Walk the body and collect every
        // disallowed key so the user sees the full list of trespassed fields.
        val fieldErrors = profiles.flatMap { checkAxisFields(it) }
        if (fieldErrors.isNotEmpty()) return Result.Failure(fieldErrors)

        // Canonical merge order, then the user's base manifest on top.
        val ordered = orderProfiles(profiles)
        val baseWithoutProfiles = stripStackProfiles(root)
        var current: RawNode = RawNode.Mapping(emptyMap())
        for (profile in ordered) {
            current = DeepMerge.merge(current, profile.body)
        }
        val merged = DeepMerge.merge(current, baseWithoutProfiles)

        return Result.Success(Manifest(merged), ordered)
    }

    // ── cardinality ──────────────────────────────────────────────────────────

    private fun checkCardinality(profiles: List<Profile>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val byAxis = profiles.groupBy { it.axis }
        val languageCount = byAxis[ProfileAxis.LANGUAGE]?.size ?: 0
        if (languageCount > 1) {
            val names = byAxis.getValue(ProfileAxis.LANGUAGE).joinToString(", ") { it.name }
            errors += ValidationError(
                path = "/stack/profiles",
                code = "profile_cardinality_violation",
                message = "Multiple language profiles in stack.profiles: $names. " +
                    "Exactly one language profile is allowed.",
                hint = "Keep one of: $names; remove the others.",
            )
        }
        // framework / capability are 0..N — no check needed.
        return errors
    }

    // ── axis-field whitelist ─────────────────────────────────────────────────

    private fun checkAxisFields(profile: Profile): List<ValidationError> {
        val spec = AxisFieldRegistry.forAxis(profile.axis)
        val errors = mutableListOf<ValidationError>()

        for ((key, value) in profile.body.entries) {
            if (key !in spec.topLevel) {
                errors += ValidationError(
                    path = "/profiles/${profile.axis.dirName}/${profile.name}.yaml#/$key",
                    code = "profile_field_outside_axis",
                    message = "Profile `${profile.name}` (axis: ${profile.axis.dirName}) " +
                        "writes top-level key `$key`, which the axis does not own.",
                    hint = "Allowed top-level keys for ${profile.axis.dirName}: " +
                        spec.topLevel.sorted().joinToString(", ") + ".",
                )
                continue
            }
            val nestedAllowed = spec.nested[key] ?: continue
            if (value is RawNode.Mapping) {
                for (subKey in value.entries.keys) {
                    if (subKey !in nestedAllowed) {
                        errors += ValidationError(
                            path = "/profiles/${profile.axis.dirName}/${profile.name}.yaml#/$key/$subKey",
                            code = "profile_field_outside_axis",
                            message = "Profile `${profile.name}` writes `$key.$subKey`, " +
                                "which axis ${profile.axis.dirName} does not own.",
                            hint = "Allowed sub-keys under `$key`: " +
                                nestedAllowed.sorted().joinToString(", ") + ".",
                        )
                    }
                }
            }
        }

        return errors
    }

    // ── ordering ─────────────────────────────────────────────────────────────

    /**
     * Canonical merge order:
     *   1. capability `security-baseline` (if present, applied first as the floor)
     *   2. language profile (single)
     *   3. framework profiles, in declared order
     *   4. remaining capability profiles, in declared order
     *
     * Within each bucket the relative order from `stack.profiles[]` is preserved.
     */
    private fun orderProfiles(profiles: List<Profile>): List<Profile> {
        val securityBaseline = profiles.filter {
            it.axis == ProfileAxis.CAPABILITY && it.name == "security-baseline"
        }
        val languages = profiles.filter { it.axis == ProfileAxis.LANGUAGE }
        val frameworks = profiles.filter { it.axis == ProfileAxis.FRAMEWORK }
        val otherCapabilities = profiles.filter {
            it.axis == ProfileAxis.CAPABILITY && it.name != "security-baseline"
        }
        return securityBaseline + languages + frameworks + otherCapabilities
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a copy of the manifest root with `stack.profiles` removed (so
     * downstream layers don't observe the marker). If `stack` becomes empty
     * as a result, it stays as an empty mapping rather than disappearing —
     * the typed model treats absent and empty identically.
     */
    private fun stripStackProfiles(root: RawNode.Mapping): RawNode.Mapping {
        val stack = root.entries["stack"]
        if (stack !is RawNode.Mapping) return root
        if ("profiles" !in stack.entries) return root
        val newStackEntries = stack.entries.filterKeys { it != "profiles" }
        val newRoot = root.entries.toMutableMap()
        newRoot["stack"] = RawNode.Mapping(newStackEntries)
        return RawNode.Mapping(newRoot)
    }
}

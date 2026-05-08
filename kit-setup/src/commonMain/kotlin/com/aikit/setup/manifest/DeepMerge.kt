package com.aikit.setup.manifest

/**
 * Deep-merges two [RawNode] trees. Used by the profile resolver to layer
 * profile bodies under the user's base manifest, and by the resolver to fold
 * `extends:` chains in the future.
 *
 * Rules:
 *  - Two [RawNode.Mapping]s → union keys. Values at common keys are merged
 *    recursively. Insertion order is preserved with base keys first, then any
 *    overlay-only keys appended.
 *  - Two [RawNode.Sequence]s → if every item on **both** sides is a Mapping
 *    carrying an `id` scalar, the sequences are merged by `id` (overlay wins
 *    per id, sub-fields merged deeply, base keeps its order, overlay-only ids
 *    are appended). Otherwise, the lists are concatenated and de-duplicated by
 *    structural equality.
 *  - Any other shape (scalar / null / type mismatch) → overlay wins. This is
 *    the "last-write-wins" rule that lets the user's base manifest override
 *    profile defaults at the leaves.
 *
 * The function is pure — it constructs new nodes; the inputs are unchanged.
 * `RawNode.Null` and a missing key are treated identically; a Null overlay
 * value preserves the explicit-null intent.
 */
object DeepMerge {

    fun merge(base: RawNode, overlay: RawNode): RawNode = when {
        base is RawNode.Mapping && overlay is RawNode.Mapping -> mergeMappings(base, overlay)
        base is RawNode.Sequence && overlay is RawNode.Sequence -> mergeSequences(base, overlay)
        else -> overlay
    }

    private fun mergeMappings(base: RawNode.Mapping, overlay: RawNode.Mapping): RawNode.Mapping {
        // Preserve base's insertion order, then append overlay-only keys in
        // their own declared order.
        val out = linkedMapOf<String, RawNode>()
        for ((k, v) in base.entries) {
            out[k] = if (overlay.entries.containsKey(k)) merge(v, overlay.entries.getValue(k)) else v
        }
        for ((k, v) in overlay.entries) {
            if (k !in out) out[k] = v
        }
        return RawNode.Mapping(out)
    }

    private fun mergeSequences(base: RawNode.Sequence, overlay: RawNode.Sequence): RawNode.Sequence {
        if (allIdKeyed(base.items) && allIdKeyed(overlay.items)) {
            return mergeIdKeyed(base, overlay)
        }
        return concatDedupe(base, overlay)
    }

    private fun allIdKeyed(items: List<RawNode>): Boolean {
        if (items.isEmpty()) return false
        return items.all { it is RawNode.Mapping && it.entries["id"] is RawNode.Scalar }
    }

    private fun mergeIdKeyed(base: RawNode.Sequence, overlay: RawNode.Sequence): RawNode.Sequence {
        // Build a base-id → mapping index. Iterate overlay; for each id either
        // merge into the base entry or append.
        val byId = linkedMapOf<String, RawNode.Mapping>()
        for (item in base.items) {
            val m = item as RawNode.Mapping
            val id = (m.entries.getValue("id") as RawNode.Scalar).value
            byId[id] = m
        }
        for (item in overlay.items) {
            val m = item as RawNode.Mapping
            val id = (m.entries.getValue("id") as RawNode.Scalar).value
            val existing = byId[id]
            byId[id] = if (existing == null) m else mergeMappings(existing, m)
        }
        return RawNode.Sequence(byId.values.toList())
    }

    private fun concatDedupe(base: RawNode.Sequence, overlay: RawNode.Sequence): RawNode.Sequence {
        val out = mutableListOf<RawNode>()
        val seen = mutableSetOf<RawNode>()
        for (n in base.items) {
            if (seen.add(n)) out += n
        }
        for (n in overlay.items) {
            if (seen.add(n)) out += n
        }
        return RawNode.Sequence(out)
    }
}

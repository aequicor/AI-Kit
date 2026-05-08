package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest
import com.aikit.setup.manifest.RawNode

/**
 * Helpers for walking the [Manifest.raw] tree without each rule having to
 * re-implement the same casts. All accessors return `null` on type mismatch
 * so a rule can report a focused error rather than throwing.
 */
internal object RawAccess {

    fun root(manifest: Manifest): RawNode.Mapping? = manifest.raw as? RawNode.Mapping

    fun child(node: RawNode?, key: String): RawNode? =
        (node as? RawNode.Mapping)?.entries?.get(key)

    fun mapping(node: RawNode?): RawNode.Mapping? = node as? RawNode.Mapping
    fun sequence(node: RawNode?): RawNode.Sequence? = node as? RawNode.Sequence
    fun scalar(node: RawNode?): String? = (node as? RawNode.Scalar)?.value

    fun isPresent(node: RawNode?): Boolean = node != null && node !is RawNode.Null

    fun stringSequenceValues(node: RawNode?): List<String> =
        sequence(node)?.items?.mapNotNull { scalar(it) }.orEmpty()
}

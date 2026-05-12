package io.aequicor.domain.model

/**
 * Represents a single page of a PDF document.
 *
 * @property index zero-based position of this page within the document.
 * @property size natural size of the page in pixels as reported by the renderer.
 */
data class PdfPage(
    val index: Int,
    val size: PdfPageSize,
)

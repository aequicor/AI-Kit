package io.aequicor.domain.model

/**
 * Represents an opened PDF document in the domain layer.
 *
 * @property pageCount total number of pages in the document.
 * @property pages ordered list of pages; size must equal [pageCount].
 */
data class PdfDocument(
    val pageCount: Int,
    val pages: List<PdfPage>,
)

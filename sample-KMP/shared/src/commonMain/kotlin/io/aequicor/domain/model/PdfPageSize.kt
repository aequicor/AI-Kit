package io.aequicor.domain.model

/**
 * Dimensions of a PDF page or render target in pixels.
 *
 * @property widthPx horizontal size in pixels.
 * @property heightPx vertical size in pixels.
 */
data class PdfPageSize(val widthPx: Int, val heightPx: Int)

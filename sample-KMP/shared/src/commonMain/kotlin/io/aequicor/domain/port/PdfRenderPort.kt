package io.aequicor.domain.port

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.model.RenderedPage

interface PdfRenderPort {
    suspend fun openDocument(bytes: ByteArray): PdfDocument

    /**
     * Renders the page at [pageIndex] scaled to approximately [targetSize].
     * Returns a [RenderedPage] whose [RenderedPage.width]/[RenderedPage.height]
     * reflect the actual pixel dimensions of the rendered bitmap.
     */
    suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): RenderedPage

    suspend fun closeDocument()
}

package io.aequicor.domain.port

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPageSize

/**
 * Output port for platform-specific PDF rendering.
 *
 * Implementations live in platform source-sets only (androidMain, jvmMain, iosMain).
 * All methods are suspend and must be called from an IO-appropriate dispatcher.
 */
interface PdfRenderPort {
    /**
     * Parses [bytes] and returns the document structure.
     * The document remains open until [closeDocument] is called.
     */
    suspend fun openDocument(bytes: ByteArray): PdfDocument

    /**
     * Renders the page at [pageIndex] scaled to [targetSize].
     *
     * @return raw ARGB8888 pixel data with length `widthPx * heightPx * 4`.
     */
    suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): ByteArray

    /** Releases all native resources held by the currently open document. */
    suspend fun closeDocument()
}

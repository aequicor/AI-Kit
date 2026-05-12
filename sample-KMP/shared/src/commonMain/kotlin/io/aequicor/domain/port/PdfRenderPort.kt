package io.aequicor.domain.port

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPageSize

interface PdfRenderPort {
    suspend fun openDocument(bytes: ByteArray): PdfDocument
    suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): ByteArray
    suspend fun closeDocument()
}

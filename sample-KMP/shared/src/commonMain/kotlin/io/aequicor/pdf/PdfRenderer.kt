package io.aequicor.pdf

import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId

expect class PdfRenderer() {
    fun openDocument(path: String): PdfDocument
    fun renderPage(docId: PdfDocumentId, pageIndex: Int, widthPx: Int, heightPx: Int): PdfPageImage
    fun closeDocument(docId: PdfDocumentId)
}

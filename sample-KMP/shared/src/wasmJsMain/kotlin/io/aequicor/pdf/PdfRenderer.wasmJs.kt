package io.aequicor.pdf

import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId

actual class PdfRenderer actual constructor() {
    actual fun openDocument(path: String): PdfDocument =
        throw NotImplementedError("PDF.js integration: use web file input + Canvas, see M1-web ADR")

    actual fun renderPage(docId: PdfDocumentId, pageIndex: Int, widthPx: Int, heightPx: Int): PdfPageImage =
        throw NotImplementedError("PDF.js integration: use web file input + Canvas, see M1-web ADR")

    actual fun closeDocument(docId: PdfDocumentId): Unit =
        throw NotImplementedError("PDF.js integration: use web file input + Canvas, see M1-web ADR")
}

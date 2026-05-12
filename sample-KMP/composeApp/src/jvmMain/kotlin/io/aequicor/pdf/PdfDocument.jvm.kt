package io.aequicor.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer

actual class PdfDocument private constructor(
    private val pdDocument: PDDocument,
    private val pdfRenderer: PDFRenderer,
) {
    actual val pageCount: Int get() = pdDocument.numberOfPages

    actual fun renderPage(pageIndex: Int, scale: Float): ImageBitmap =
        pdfRenderer.renderImage(pageIndex, scale).toComposeImageBitmap()

    actual fun close() = pdDocument.close()

    actual companion object {
        actual fun open(bytes: ByteArray): PdfDocument {
            val doc = Loader.loadPDF(bytes)
            return PdfDocument(doc, PDFRenderer(doc))
        }
    }
}

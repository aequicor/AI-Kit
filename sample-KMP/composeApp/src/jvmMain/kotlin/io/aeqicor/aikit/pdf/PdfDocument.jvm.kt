package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

actual class PdfDocument actual constructor(path: String) {
    private val document: PDDocument = Loader.loadPDF(File(path))
    private val renderer: PDFRenderer = PDFRenderer(document)

    actual val pageCount: Int = document.numberOfPages

    actual fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): ImageBitmap {
        val page = document.getPage(pageIndex)
        val dpi = if (widthPx > 0) widthPx * 72f / page.mediaBox.width else 96f
        val bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi)
        return bufferedImage.toComposeImageBitmap()
    }

    actual fun close() = document.close()
}

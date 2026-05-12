package io.aequicor.pdf

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.port.PdfRenderPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class JvmPdfRenderAdapter : PdfRenderPort {

    private var document: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null

    override suspend fun openDocument(bytes: ByteArray): PdfDocument = withContext(Dispatchers.IO) {
        closeDocumentInternal()

        val doc = PDDocument.load(ByteArrayInputStream(bytes))
        document = doc
        pdfRenderer = PDFRenderer(doc)

        val pages = (0 until doc.numberOfPages).map { i ->
            val page = doc.getPage(i)
            val w = page.mediaBox.width.toInt()
            val h = page.mediaBox.height.toInt()
            PdfPage(index = i, size = PdfPageSize(widthPx = w, heightPx = h))
        }
        PdfDocument(pageCount = doc.numberOfPages, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): ByteArray =
        withContext(Dispatchers.IO) {
            val renderer = checkNotNull(pdfRenderer) { "No document open" }
            val doc = checkNotNull(document) { "No document open" }
            val page = doc.getPage(pageIndex)
            val naturalW = page.mediaBox.width
            val dpi = (targetSize.widthPx / naturalW) * 72f
            val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, dpi, org.apache.pdfbox.rendering.ImageType.ARGB)
            argb8888Bytes(image)
        }

    override suspend fun closeDocument() = withContext(Dispatchers.IO) {
        closeDocumentInternal()
    }

    private fun closeDocumentInternal() {
        try {
            document?.close()
        } finally {
            document = null
            pdfRenderer = null
        }
    }

    private fun argb8888Bytes(image: BufferedImage): ByteArray {
        val w = image.width
        val h = image.height
        val buf = ByteArray(w * h * 4)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = image.getRGB(x, y)
                buf[i++] = ((argb shr 16) and 0xFF).toByte() // R
                buf[i++] = ((argb shr 8) and 0xFF).toByte()  // G
                buf[i++] = (argb and 0xFF).toByte()           // B
                buf[i++] = ((argb shr 24) and 0xFF).toByte() // A
            }
        }
        return buf
    }
}

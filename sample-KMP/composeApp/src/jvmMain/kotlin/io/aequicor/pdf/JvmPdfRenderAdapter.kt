package io.aequicor.pdf

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.model.RenderedPage
import io.aequicor.domain.port.PdfRenderPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage

class JvmPdfRenderAdapter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfRenderPort {

    private companion object {
        const val PDF_BASE_DPI = 72f
        const val ARGB_BYTES_PER_PIXEL = 4
        const val ARGB_RED_SHIFT = 16
        const val ARGB_GREEN_SHIFT = 8
        const val ARGB_ALPHA_SHIFT = 24
        const val CHANNEL_MASK = 0xFF
        const val ROTATION_90 = 90
        const val ROTATION_270 = 270
    }

    private val renderMutex = Mutex()
    private var document: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null

    override suspend fun openDocument(bytes: ByteArray): PdfDocument = withContext(ioDispatcher) {
        closeDocumentInternal()

        val doc = Loader.loadPDF(bytes)
        document = doc
        pdfRenderer = PDFRenderer(doc)

        val pages = (0 until doc.numberOfPages).map { i ->
            val page = doc.getPage(i)
            val rotation = page.rotation
            val mediaW = page.mediaBox.width
            val mediaH = page.mediaBox.height
            // PDFBox renders with rotation applied; store post-rotation visual dimensions.
            val (w, h) = if (rotation == ROTATION_90 || rotation == ROTATION_270) {
                mediaH.toInt() to mediaW.toInt()
            } else {
                mediaW.toInt() to mediaH.toInt()
            }
            PdfPage(index = i, size = PdfPageSize(widthPx = w, heightPx = h))
        }
        PdfDocument(pageCount = doc.numberOfPages, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): RenderedPage =
        renderMutex.withLock {
            withContext(ioDispatcher) {
                val renderer = checkNotNull(pdfRenderer) { "No document open" }
                val doc = checkNotNull(document) { "No document open" }
                val page = doc.getPage(pageIndex)
                // Use the visual (post-rotation) width for DPI so PDFBox output matches targetSize.
                val rotation = page.rotation
                val naturalW = if (rotation == ROTATION_90 || rotation == ROTATION_270) {
                    page.mediaBox.height
                } else {
                    page.mediaBox.width
                }
                val dpi = (targetSize.widthPx / naturalW) * PDF_BASE_DPI
                val image: BufferedImage =
                    renderer.renderImageWithDPI(pageIndex, dpi, org.apache.pdfbox.rendering.ImageType.ARGB)
                // Use actual image dimensions — they may differ by ±1px from targetSize due to rounding.
                RenderedPage(argb8888Bytes(image), image.width, image.height)
            }
        }

    override suspend fun closeDocument() = withContext(ioDispatcher) {
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
        val buf = ByteArray(w * h * ARGB_BYTES_PER_PIXEL)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = image.getRGB(x, y)
                buf[i++] = ((argb shr ARGB_RED_SHIFT) and CHANNEL_MASK).toByte()
                buf[i++] = ((argb shr ARGB_GREEN_SHIFT) and CHANNEL_MASK).toByte()
                buf[i++] = (argb and CHANNEL_MASK).toByte()
                buf[i++] = ((argb shr ARGB_ALPHA_SHIFT) and CHANNEL_MASK).toByte()
            }
        }
        return buf
    }
}

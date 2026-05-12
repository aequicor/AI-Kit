package io.aequicor.pdf

import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.PdfPage
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual class PdfRenderer actual constructor() {

    private val lock = ReentrantLock()
    private val openDocs = mutableMapOf<String, PDDocument>()

    actual fun openDocument(path: String): PdfDocument = lock.withLock {
        val pdDoc = Loader.loadPDF(File(path))
        val id = PdfDocumentId(path)
        openDocs[path] = pdDoc
        val pages = (0 until pdDoc.numberOfPages).map { i ->
            val mediaBox = pdDoc.getPage(i).mediaBox
            PdfPage(
                index = i,
                widthPx = mediaBox.width.toInt(),
                heightPx = mediaBox.height.toInt(),
            )
        }
        PdfDocument(id = id, pageCount = pdDoc.numberOfPages, pages = pages)
    }

    actual fun renderPage(
        docId: PdfDocumentId,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageImage = lock.withLock {
        val pdDoc = openDocs[docId.value] ?: error("Document not open: ${docId.value}")
        val page = pdDoc.getPage(pageIndex)
        val mediaBox = page.mediaBox
        // Derive DPI so the rendered image matches the requested pixel dimensions.
        val dpi = (widthPx / mediaBox.width * 72f).coerceAtLeast(72f)
        // ImageType.RGB fills the background with white; ARGB leaves blank pages fully transparent.
        val image = PDFRenderer(pdDoc).renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
        val w = image.width
        val h = image.height
        val intArray = IntArray(w * h)
        image.getRGB(0, 0, w, h, intArray, 0, w)
        val bytes = ByteBuffer.allocate(intArray.size * 4).also { buf ->
            intArray.forEach { buf.putInt(it) }
        }.array()
        PdfPageImage(widthPx = w, heightPx = h, pixels = bytes)
    }

    actual fun closeDocument(docId: PdfDocumentId): Unit = lock.withLock {
        openDocs.remove(docId.value)?.close()
    }
}

package io.aequicor.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.PdfPage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual class PdfRenderer actual constructor() {

    private val lock = ReentrantLock()
    private val openRenderers = mutableMapOf<String, AndroidPdfRenderer>()
    private val openFds = mutableMapOf<String, ParcelFileDescriptor>()

    actual fun openDocument(path: String): PdfDocument = lock.withLock {
        val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = AndroidPdfRenderer(fd)
        val id = PdfDocumentId(path)
        openRenderers[path] = renderer
        openFds[path] = fd
        val pages = (0 until renderer.pageCount).map { i ->
            renderer.openPage(i).use { page ->
                PdfPage(index = i, widthPx = page.width, heightPx = page.height)
            }
        }
        PdfDocument(id = id, pageCount = renderer.pageCount, pages = pages)
    }

    actual fun renderPage(
        docId: PdfDocumentId,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageImage = lock.withLock {
        val renderer = openRenderers[docId.value]
            ?: error("Document not open: ${docId.value}")
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        renderer.openPage(pageIndex).use { page -> page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        val intArray = IntArray(widthPx * heightPx)
        bitmap.getPixels(intArray, 0, widthPx, 0, 0, widthPx, heightPx)
        bitmap.recycle()
        val bytes = ByteBuffer.allocate(intArray.size * 4).also { buf ->
            intArray.forEach { buf.putInt(it) }
        }.array()
        PdfPageImage(widthPx = widthPx, heightPx = heightPx, pixels = bytes)
    }

    actual fun closeDocument(docId: PdfDocumentId): Unit = lock.withLock {
        openRenderers.remove(docId.value)?.close()
        openFds.remove(docId.value)?.close()
    }
}

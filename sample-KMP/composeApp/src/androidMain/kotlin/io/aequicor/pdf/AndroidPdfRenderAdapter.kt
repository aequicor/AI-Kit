package io.aequicor.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.port.PdfRenderPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidPdfRenderAdapter : PdfRenderPort {

    private var renderer: PdfRenderer? = null
    private var tempFile: File? = null

    override suspend fun openDocument(bytes: ByteArray): PdfDocument = withContext(Dispatchers.IO) {
        closeDocumentInternal()

        val file = File.createTempFile("pdf_", ".pdf").also { it.deleteOnExit() }
        file.writeBytes(bytes)
        tempFile = file

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        renderer = r

        val pages = (0 until r.pageCount).map { i ->
            r.openPage(i).use { page ->
                PdfPage(index = i, size = PdfPageSize(widthPx = page.width, heightPx = page.height))
            }
        }
        PdfDocument(pageCount = r.pageCount, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): ByteArray =
        withContext(Dispatchers.IO) {
            val r = checkNotNull(renderer) { "No document open" }
            r.openPage(pageIndex).use { page ->
                val bitmap = createBitmap(targetSize.widthPx, targetSize.heightPx)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val buf = ByteArray(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(buf))
                bitmap.recycle()
                buf
            }
        }

    override suspend fun closeDocument() = withContext(Dispatchers.IO) {
        closeDocumentInternal()
    }

    private fun closeDocumentInternal() {
        renderer?.close()
        renderer = null
        tempFile?.delete()
        tempFile = null
    }
}

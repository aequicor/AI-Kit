package io.aequicor.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

actual class PdfDocument private constructor(
    private val renderer: PdfRenderer,
    private val tempFile: File,
) {
    actual val pageCount: Int get() = renderer.pageCount

    actual fun renderPage(pageIndex: Int, scale: Float): ImageBitmap {
        val page = renderer.openPage(pageIndex)
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap.asImageBitmap()
    }

    actual fun close() {
        renderer.close()
        tempFile.delete()
    }

    actual companion object {
        actual fun open(bytes: ByteArray): PdfDocument {
            val tempFile = File.createTempFile("pdf_", ".pdf")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfDocument(PdfRenderer(pfd), tempFile)
        }
    }
}

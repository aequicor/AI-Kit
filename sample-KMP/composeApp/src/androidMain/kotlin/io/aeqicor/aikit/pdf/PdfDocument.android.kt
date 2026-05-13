package io.aeqicor.aikit.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

actual class PdfDocument actual constructor(path: String) {
    private val fd: ParcelFileDescriptor = if (path.startsWith("content://")) {
        AndroidContext.context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
            ?: error("Cannot open content URI: $path")
    } else {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    private val renderer: PdfRenderer = PdfRenderer(fd)

    actual val pageCount: Int = renderer.pageCount

    actual fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): ImageBitmap {
        val page = renderer.openPage(pageIndex)
        val w = if (widthPx > 0) widthPx else page.width
        val h = if (heightPx > 0) heightPx else page.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap.asImageBitmap()
    }

    actual fun close() {
        renderer.close()
        fd.close()
    }
}

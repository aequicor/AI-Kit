package io.aequicor.pdf

import androidx.compose.ui.graphics.ImageBitmap

actual class PdfDocument {
    actual val pageCount: Int get() = throw UnsupportedOperationException("PDF not supported on iOS")
    actual fun renderPage(pageIndex: Int, scale: Float): ImageBitmap = throw UnsupportedOperationException("PDF not supported on iOS")
    actual fun close() {}

    actual companion object {
        actual fun open(bytes: ByteArray): PdfDocument = throw UnsupportedOperationException("PDF not supported on iOS")
    }
}

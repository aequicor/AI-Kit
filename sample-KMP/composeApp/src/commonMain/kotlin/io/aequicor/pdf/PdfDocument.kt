package io.aequicor.pdf

import androidx.compose.ui.graphics.ImageBitmap

expect class PdfDocument {
    val pageCount: Int
    fun renderPage(pageIndex: Int, scale: Float): ImageBitmap
    fun close()

    companion object {
        fun open(bytes: ByteArray): PdfDocument
    }
}

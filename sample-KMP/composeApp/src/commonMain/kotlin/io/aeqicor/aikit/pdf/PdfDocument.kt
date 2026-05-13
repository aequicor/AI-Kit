package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap

expect class PdfDocument(path: String) {
    val pageCount: Int
    fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): ImageBitmap
    fun close()
}

fun loadPdf(path: String): PdfDocument = PdfDocument(path)

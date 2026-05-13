package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap

actual class PdfDocument actual constructor(path: String) {
    actual val pageCount: Int = 0

    actual fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): ImageBitmap =
        ImageBitmap(maxOf(1, widthPx), maxOf(1, heightPx))

    actual fun findMatches(query: String): List<SearchMatch> = emptyList()

    actual fun close() {}
}

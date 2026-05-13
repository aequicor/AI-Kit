package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap

data class PdfViewerState(
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val renderedPages: Map<Int, ImageBitmap> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewportWidth: Int = 0,
    val scrollToPage: Int? = null,
    val zoomScale: Float = 1f,
    val renderToken: Long = 0
)

package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap

data class PdfViewerState(
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val renderedPage: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewportWidth: Int = 0
)

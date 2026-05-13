package io.aeqicor.aikit.pdf

import androidx.compose.ui.graphics.ImageBitmap

data class PdfViewerState(
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val renderedPages: Map<Int, ImageBitmap> = emptyMap(),
    val thumbnailPages: Map<Int, ImageBitmap> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewportWidth: Int = 0,
    val actualViewportWidth: Int = 0,
    val scrollToPage: Int? = null,
    val zoomScale: Float = 1f,
    val renderToken: Long = 0,
    val isSidebarOpen: Boolean = false,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1
)

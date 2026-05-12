package io.aequicor.viewer

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.RenderedPage

data class ViewerState(
    val document: PdfDocument? = null,
    val renderedPages: Map<Int, RenderedPage> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val zoom: Float = DEFAULT_ZOOM,
    val offsetX: Float = 0f,
    val viewportWidthPx: Int = 0,
) {
    companion object {
        const val DEFAULT_ZOOM = 1f
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 8f
    }
}

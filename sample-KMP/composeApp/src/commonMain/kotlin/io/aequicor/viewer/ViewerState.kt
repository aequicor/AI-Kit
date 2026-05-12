package io.aequicor.viewer

import io.aequicor.domain.model.PdfDocument

data class ViewerState(
    val document: PdfDocument? = null,
    val renderedPages: Map<Int, ByteArray> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val zoom: Float = DEFAULT_ZOOM,
) {
    companion object {
        const val DEFAULT_ZOOM = 1f
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 8f
    }
}

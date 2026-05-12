package io.aequicor.pdf.domain

const val MIN_SCALE = 0.25f
const val MAX_SCALE = 8f

data class ViewportState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val currentPage: Int = 0,
)

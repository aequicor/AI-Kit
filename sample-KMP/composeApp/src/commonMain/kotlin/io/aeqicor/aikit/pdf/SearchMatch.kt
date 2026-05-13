package io.aeqicor.aikit.pdf

data class SearchMatch(
    val pageIndex: Int,
    val text: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val hasCoords: Boolean = false
)

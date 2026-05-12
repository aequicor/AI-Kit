package io.aequicor.pdf.domain

sealed class DrawingTool {
    data class Brush(val widthDp: Float, val color: Long) : DrawingTool() {
        companion object {
            const val DEFAULT_WIDTH_DP = 4f
            const val COLOR_BLACK = 0xFF000000L
        }
    }
    data class Eraser(val widthDp: Float) : DrawingTool()
}

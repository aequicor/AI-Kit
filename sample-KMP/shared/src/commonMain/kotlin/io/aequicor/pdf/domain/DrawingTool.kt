package io.aequicor.pdf.domain

sealed class DrawingTool {
    data class Brush(val widthDp: Float, val color: Long) : DrawingTool()
    data class Eraser(val widthDp: Float) : DrawingTool()
}

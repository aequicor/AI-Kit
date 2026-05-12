package io.aequicor.pdf.domain

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val timestamp: Long,
)

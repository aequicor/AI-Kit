package io.aequicor.pdf.ui.input

data class StylusEvent(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val type: EventType,
) {
    enum class EventType { DOWN, MOVE, UP }
}

package io.aequicor.pdf

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.scrollWheelZoom(onZoom: (delta: Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (scrollDelta != 0f) onZoom(-scrollDelta)
                }
            }
        }
    }

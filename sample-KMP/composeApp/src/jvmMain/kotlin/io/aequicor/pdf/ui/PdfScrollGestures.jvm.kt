package io.aequicor.pdf.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.ctrlScrollZoom(onZoom: (factor: Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type == PointerEventType.Scroll &&
                    event.keyboardModifiers.isCtrlPressed
                ) {
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                    val factor = if (dy > 0) 0.9f else 1.1f
                    onZoom(factor)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

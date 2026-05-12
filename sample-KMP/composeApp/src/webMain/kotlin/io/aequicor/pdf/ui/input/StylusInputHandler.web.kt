package io.aequicor.pdf.ui.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.stylusInput(onEvent: (StylusEvent) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                if (change.type == PointerType.Touch) continue

                val eventType = when (event.type) {
                    PointerEventType.Press -> StylusEvent.EventType.DOWN
                    PointerEventType.Move -> StylusEvent.EventType.MOVE
                    PointerEventType.Release -> StylusEvent.EventType.UP
                    else -> continue
                }

                onEvent(
                    StylusEvent(
                        x = change.position.x,
                        y = change.position.y,
                        pressure = change.pressure,
                        tiltX = 0f,
                        tiltY = 0f,
                        type = eventType,
                    )
                )
            }
        }
    }

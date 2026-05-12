package io.aequicor.pdf.ui.input

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin

actual fun Modifier.stylusInput(onEvent: (StylusEvent) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            var hasStylusInStream = false
            while (true) {
                val event = awaitPointerEvent()
                val me = event.nativeEvent as? MotionEvent ?: continue
                val toolType = me.getToolType(0)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
                val isTouch = toolType == MotionEvent.TOOL_TYPE_FINGER

                when (me.actionMasked) {
                    MotionEvent.ACTION_DOWN -> hasStylusInStream = isStylus
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hasStylusInStream = false
                }

                // Palm rejection: drop touch events once a stylus is active in this gesture
                if (isTouch && hasStylusInStream) continue

                val eventType = when (me.actionMasked) {
                    MotionEvent.ACTION_DOWN -> StylusEvent.EventType.DOWN
                    MotionEvent.ACTION_MOVE -> StylusEvent.EventType.MOVE
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> StylusEvent.EventType.UP
                    else -> continue
                }

                val tiltAngle = me.getAxisValue(MotionEvent.AXIS_TILT)
                val orientation = me.getAxisValue(MotionEvent.AXIS_ORIENTATION)

                onEvent(
                    StylusEvent(
                        x = me.x,
                        y = me.y,
                        pressure = me.getPressure(0),
                        tiltX = (tiltAngle * cos(orientation)).toFloat(),
                        tiltY = (tiltAngle * sin(orientation)).toFloat(),
                        type = eventType,
                    )
                )
            }
        }
    }

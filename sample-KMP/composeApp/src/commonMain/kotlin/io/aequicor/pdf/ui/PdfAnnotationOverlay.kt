package io.aequicor.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.StrokePoint
import io.aequicor.pdf.ui.stroke.catmullRomPath
import io.aequicor.pdf.ui.stroke.drawStroke

@Composable
fun PdfAnnotationOverlay(
    layer: AnnotationLayer,
    activeStroke: List<StrokePoint>?,
    activeTool: DrawingTool? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // Bottom layer: committed strokes — redraws only when layer changes
        for (stroke in layer.strokes) {
            drawStroke(stroke)
        }

        // Top layer: current in-progress stroke — redraws on every pointer event
        if (activeStroke != null && activeStroke.size >= 2) {
            val path = catmullRomPath(activeStroke)
            val brush = activeTool as? DrawingTool.Brush
            val color = if (brush != null) Color(brush.color) else Color.Black
            val widthPx = if (brush != null) brush.widthDp.dp.toPx() else 4f
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = widthPx.coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

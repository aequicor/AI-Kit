package io.aequicor.pdf.ui.stroke

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.Stroke as DomainStroke

fun DrawScope.drawStroke(stroke: DomainStroke) {
    val points = stroke.points
    if (points.isEmpty()) return

    val path = catmullRomPath(points)
    val avgPressure = points.map { it.pressure }.average().toFloat().coerceIn(0.1f, 1f)

    when (val tool = stroke.tool) {
        is DrawingTool.Brush -> {
            val widthPx = tool.widthDp.dp.toPx() * avgPressure
            drawPath(
                path = path,
                color = Color(tool.color),
                style = Stroke(
                    width = widthPx.coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
                blendMode = BlendMode.SrcOver,
            )
        }
        is DrawingTool.Eraser -> {
            val widthPx = tool.widthDp.dp.toPx()
            drawPath(
                path = path,
                color = Color.Transparent,
                style = Stroke(
                    width = widthPx.coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
                blendMode = BlendMode.Clear,
            )
        }
    }
}

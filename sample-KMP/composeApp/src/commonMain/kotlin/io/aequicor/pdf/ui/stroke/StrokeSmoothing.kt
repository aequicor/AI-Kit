package io.aequicor.pdf.ui.stroke

import androidx.compose.ui.graphics.Path
import io.aequicor.pdf.domain.StrokePoint

fun catmullRomPath(points: List<StrokePoint>): Path {
    if (points.size < 2) return Path()

    val path = Path()
    path.moveTo(points[0].x, points[0].y)

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    // Catmull-Rom → cubic Bézier: for segment [p1→p2] with neighbours p0 and p3:
    //   CP1 = p1 + (p2 - p0) / 6
    //   CP2 = p2 - (p3 - p1) / 6
    for (i in 0 until points.size - 1) {
        val p0 = if (i == 0) points[0] else points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }

    return path
}

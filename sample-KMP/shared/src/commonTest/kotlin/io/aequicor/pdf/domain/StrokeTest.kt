package io.aequicor.pdf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StrokeTest {

    @Test
    fun strokeIdValueEquality() {
        assertEquals(StrokeId("abc"), StrokeId("abc"))
        assertNotEquals(StrokeId("abc"), StrokeId("xyz"))
    }

    @Test
    fun strokeHoldsToolAndPoints() {
        val tool = DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L)
        val points = listOf(
            StrokePoint(0f, 0f, 1f, 0f, 0f, 0L),
            StrokePoint(10f, 10f, 0.8f, 0f, 0f, 1L),
        )
        val stroke = Stroke(id = StrokeId("s1"), tool = tool, points = points)
        assertEquals(tool, stroke.tool)
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun annotationLayerPreservesOrder() {
        val docId = PdfDocumentId("doc1")
        val strokes = (1..5).map { i ->
            Stroke(
                id = StrokeId("s$i"),
                tool = DrawingTool.Brush(widthDp = 2f, color = 0xFF0000FFL),
                points = listOf(StrokePoint(i.toFloat(), i.toFloat(), 1f, 0f, 0f, i.toLong())),
            )
        }
        val layer = AnnotationLayer(docId, pageIndex = 0, strokes = strokes)
        assertEquals(5, layer.strokes.size)
        assertEquals("s1", layer.strokes.first().id.value)
        assertEquals("s5", layer.strokes.last().id.value)
    }

    @Test
    fun brushEraserAreDistinctTools() {
        val brush = DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L)
        val eraser = DrawingTool.Eraser(widthDp = 16f)
        assertTrue(brush is DrawingTool.Brush)
        assertTrue(eraser is DrawingTool.Eraser)
    }

    @Test
    fun strokePointHoldsAllFields() {
        val p = StrokePoint(x = 1f, y = 2f, pressure = 0.7f, tiltX = 0.1f, tiltY = 0.2f, timestamp = 42L)
        assertEquals(1f, p.x)
        assertEquals(2f, p.y)
        assertEquals(0.7f, p.pressure)
        assertEquals(0.1f, p.tiltX)
        assertEquals(0.2f, p.tiltY)
        assertEquals(42L, p.timestamp)
    }
}

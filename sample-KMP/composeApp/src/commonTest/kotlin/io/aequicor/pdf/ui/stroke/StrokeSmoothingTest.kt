package io.aequicor.pdf.ui.stroke

import io.aequicor.pdf.domain.StrokePoint
import kotlin.test.Test
import kotlin.test.assertNotNull

class StrokeSmoothingTest {

    private fun point(x: Float, y: Float) =
        StrokePoint(x = x, y = y, pressure = 1f, tiltX = 0f, tiltY = 0f, timestamp = 0L)

    @Test
    fun emptyPointsReturnsPath() {
        val path = catmullRomPath(emptyList())
        assertNotNull(path)
    }

    @Test
    fun singlePointReturnsPath() {
        val path = catmullRomPath(listOf(point(0f, 0f)))
        assertNotNull(path)
    }

    @Test
    fun twoPointsReturnsPath() {
        val path = catmullRomPath(listOf(point(0f, 0f), point(100f, 100f)))
        assertNotNull(path)
    }

    @Test
    fun threePointsReturnsPath() {
        val path = catmullRomPath(listOf(point(0f, 0f), point(50f, 100f), point(100f, 0f)))
        assertNotNull(path)
    }

    @Test
    fun tenPointsReturnsPath() {
        val points = (0 until 10).map { i -> point(i * 10f, (i % 3) * 20f) }
        val path = catmullRomPath(points)
        assertNotNull(path)
    }

    @Test
    fun collinearPointsReturnsPath() {
        // All points on a horizontal line — degenerate but must not crash
        val points = (0 until 5).map { i -> point(i * 10f, 0f) }
        val path = catmullRomPath(points)
        assertNotNull(path)
    }
}

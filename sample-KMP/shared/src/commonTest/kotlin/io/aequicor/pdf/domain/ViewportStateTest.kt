package io.aequicor.pdf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewportStateTest {

    @Test
    fun defaultValuesAreCorrect() {
        val state = ViewportState()
        assertEquals(1f, state.scale)
        assertEquals(0f, state.offsetX)
        assertEquals(0f, state.offsetY)
        assertEquals(0, state.currentPage)
    }

    @Test
    fun minScaleConstantIsCorrect() {
        assertEquals(0.25f, MIN_SCALE)
    }

    @Test
    fun maxScaleConstantIsCorrect() {
        assertEquals(8f, MAX_SCALE)
    }

    @Test
    fun scaleClampedToMin() {
        val clamped = 0.1f.coerceIn(MIN_SCALE, MAX_SCALE)
        assertEquals(MIN_SCALE, clamped)
    }

    @Test
    fun scaleClampedToMax() {
        val clamped = 10f.coerceIn(MIN_SCALE, MAX_SCALE)
        assertEquals(MAX_SCALE, clamped)
    }

    @Test
    fun scaleWithinRangeUnchanged() {
        val scale = 2f
        assertEquals(scale, scale.coerceIn(MIN_SCALE, MAX_SCALE))
    }

    @Test
    fun copyPreservesUnchangedFields() {
        val state = ViewportState(scale = 2f, offsetX = 10f, offsetY = 20f, currentPage = 3)
        val updated = state.copy(scale = 3f)
        assertEquals(3f, updated.scale)
        assertEquals(10f, updated.offsetX)
        assertEquals(20f, updated.offsetY)
        assertEquals(3, updated.currentPage)
    }

    @Test
    fun equalityByValue() {
        val a = ViewportState(scale = 1.5f, offsetX = 5f, offsetY = 10f, currentPage = 2)
        val b = ViewportState(scale = 1.5f, offsetX = 5f, offsetY = 10f, currentPage = 2)
        assertEquals(a, b)
    }
}

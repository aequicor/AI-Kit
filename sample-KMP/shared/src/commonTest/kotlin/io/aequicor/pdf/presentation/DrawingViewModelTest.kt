package io.aequicor.pdf.presentation

import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.StrokePoint
import io.aequicor.pdf.domain.repository.AnnotationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeAnnotationRepository
    private lateinit var vm: DrawingViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeAnnotationRepository()
        vm = DrawingViewModel(fakeRepo, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun endStrokeAddsStrokeToLayer() = runTest {
        val tool = DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L)
        vm.beginStroke(tool)
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        vm.addPoint(StrokePoint(10f, 10f, 0.8f, 0f, 0f, 1L))
        vm.endStroke()

        assertEquals(1, vm.layer.value.strokes.size)
        assertEquals(tool, vm.layer.value.strokes[0].tool)
    }

    @Test
    fun endStrokeWithNoPointsDoesNothing() = runTest {
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.endStroke()
        assertEquals(0, vm.layer.value.strokes.size)
    }

    @Test
    fun activePointsClearedAfterEndStroke() = runTest {
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        assertEquals(1, vm.activePoints.value.size)
        vm.endStroke()
        assertTrue(vm.activePoints.value.isEmpty())
    }

    @Test
    fun undoRemovesLastStroke() = runTest {
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        vm.endStroke()
        assertEquals(1, vm.layer.value.strokes.size)

        vm.undo()
        assertEquals(0, vm.layer.value.strokes.size)
    }

    @Test
    fun redoReappliesUndoneStroke() = runTest {
        val tool = DrawingTool.Brush(4f, 0xFF0000FFL)
        vm.beginStroke(tool)
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        vm.endStroke()
        vm.undo()
        assertEquals(0, vm.layer.value.strokes.size)

        vm.redo()
        assertEquals(1, vm.layer.value.strokes.size)
        assertEquals(tool, vm.layer.value.strokes[0].tool)
    }

    @Test
    fun undoOnEmptyStackDoesNothing() = runTest {
        vm.undo()
        assertEquals(0, vm.layer.value.strokes.size)
    }

    @Test
    fun redoClearedAfterNewStroke() = runTest {
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        vm.endStroke()
        vm.undo()
        // new stroke clears redo
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.addPoint(StrokePoint(5f, 5f, 1f, 0f, 0f, 0L))
        vm.endStroke()
        vm.undo()
        // redo has one item (the new stroke), but the original is gone
        vm.redo()
        assertEquals(1, vm.layer.value.strokes.size)
    }

    @Test
    fun endStrokeSavesLayerToRepo() = runTest {
        vm.beginStroke(DrawingTool.Brush(4f, 0xFF000000L))
        vm.addPoint(StrokePoint(0f, 0f, 1f, 0f, 0f, 0L))
        vm.endStroke()
        assertTrue(fakeRepo.savedLayers.isNotEmpty())
        assertNotNull(fakeRepo.savedLayers.last())
    }
}

private class FakeAnnotationRepository : AnnotationRepository {
    val savedLayers = mutableListOf<AnnotationLayer>()

    override suspend fun getLayer(docId: PdfDocumentId, page: Int): AnnotationLayer =
        AnnotationLayer(docId, page, emptyList())

    override suspend fun saveLayer(layer: AnnotationLayer) {
        savedLayers.add(layer)
    }
}

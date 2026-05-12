package io.aequicor.pdf.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.Stroke
import io.aequicor.pdf.domain.StrokeId
import io.aequicor.pdf.domain.StrokePoint
import io.aequicor.pdf.domain.repository.AnnotationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DrawingViewModel(private val repo: AnnotationRepository) : ViewModel() {

    private val _layer = MutableStateFlow(AnnotationLayer(PdfDocumentId(""), 0, emptyList()))
    val layer: StateFlow<AnnotationLayer> = _layer.asStateFlow()

    private val _activePoints = MutableStateFlow<List<StrokePoint>>(emptyList())
    val activePoints: StateFlow<List<StrokePoint>> = _activePoints.asStateFlow()

    private var currentTool: DrawingTool = DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L)

    private val undoStack = ArrayDeque<DrawCommand>()
    private val redoStack = ArrayDeque<DrawCommand>()

    fun loadPage(docId: PdfDocumentId, page: Int) {
        viewModelScope.launch {
            _layer.value = repo.getLayer(docId, page)
        }
    }

    fun beginStroke(tool: DrawingTool) {
        currentTool = tool
        _activePoints.value = emptyList()
    }

    fun addPoint(point: StrokePoint) {
        _activePoints.value = _activePoints.value + point
    }

    fun endStroke() {
        val points = _activePoints.value
        if (points.isEmpty()) return
        _activePoints.value = emptyList()

        val stroke = Stroke(
            id = StrokeId(generateId()),
            tool = currentTool,
            points = points,
        )
        applyCommand(DrawCommand.AddStroke(stroke))
    }

    fun undo() {
        val cmd = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(cmd)
        _layer.value = cmd.revert(_layer.value)
        saveCurrentLayer()
    }

    fun redo() {
        val cmd = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(cmd)
        _layer.value = cmd.apply(_layer.value)
        saveCurrentLayer()
    }

    private fun applyCommand(cmd: DrawCommand) {
        if (undoStack.size >= MAX_UNDO) undoStack.removeFirst()
        undoStack.addLast(cmd)
        redoStack.clear()
        _layer.value = cmd.apply(_layer.value)
        saveCurrentLayer()
    }

    private fun saveCurrentLayer() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveLayer(_layer.value)
        }
    }

    companion object {
        private const val MAX_UNDO = 50
    }
}

private fun generateId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16).map { chars.random() }.joinToString("")
}

private sealed interface DrawCommand {
    fun apply(layer: AnnotationLayer): AnnotationLayer
    fun revert(layer: AnnotationLayer): AnnotationLayer

    data class AddStroke(val stroke: Stroke) : DrawCommand {
        override fun apply(layer: AnnotationLayer) =
            layer.copy(strokes = layer.strokes + stroke)

        override fun revert(layer: AnnotationLayer) =
            layer.copy(strokes = layer.strokes.filter { it.id != stroke.id })
    }

    data class RemoveStroke(val stroke: Stroke) : DrawCommand {
        override fun apply(layer: AnnotationLayer) =
            layer.copy(strokes = layer.strokes.filter { it.id != stroke.id })

        override fun revert(layer: AnnotationLayer) =
            layer.copy(strokes = layer.strokes + stroke)
    }
}

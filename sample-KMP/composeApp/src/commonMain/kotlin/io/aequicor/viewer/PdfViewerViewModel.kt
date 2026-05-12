package io.aequicor.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.port.PdfRenderPort
import io.aequicor.domain.usecase.OpenDocumentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class PdfViewerViewModel(
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val port: PdfRenderPort,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private val renderJobs = mutableMapOf<Int, Job>()

    fun openDocument(bytes: ByteArray) {
        cancelAllRenderJobs()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, document = null, renderedPages = emptyMap(), offsetX = 0f) }
            runCatching { openDocumentUseCase(bytes) }
                .onSuccess { doc -> _state.update { it.copy(document = doc, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun setViewportWidth(px: Int) {
        if (px <= 0) return
        val current = _state.value.viewportWidthPx
        if (current == 0 || abs(px - current).toFloat() / current > 0.1f) {
            cancelAllRenderJobs()
            _state.update { it.copy(viewportWidthPx = px, renderedPages = emptyMap()) }
        }
    }

    fun ensurePageRendered(pageIndex: Int) {
        val s = _state.value
        if (s.renderedPages.containsKey(pageIndex)) return
        if (renderJobs[pageIndex]?.isActive == true) return
        val page = s.document?.pages?.getOrNull(pageIndex) ?: return
        val vpW = s.viewportWidthPx.takeIf { it > 0 } ?: page.size.widthPx
        val renderH = (vpW.toFloat() * page.size.heightPx / page.size.widthPx).toInt()
        val renderSize = PdfPageSize(vpW, renderH)
        renderJobs[pageIndex] = viewModelScope.launch {
            runCatching { port.renderPage(pageIndex, renderSize) }
                .onSuccess { bytes ->
                    _state.update {
                        it.copy(renderedPages = it.renderedPages + (pageIndex to RenderedPage(bytes, vpW, renderH)))
                    }
                }
        }
    }

    fun cancelPageRender(pageIndex: Int) {
        renderJobs.remove(pageIndex)?.cancel()
    }

    fun onZoomChange(scaleDelta: Float) {
        _state.update { s ->
            val newZoom = (s.zoom * scaleDelta).coerceIn(ViewerState.MIN_ZOOM, ViewerState.MAX_ZOOM)
            val actualScale = newZoom / s.zoom
            s.copy(zoom = newZoom, offsetX = s.offsetX * actualScale)
        }
    }

    fun onPanX(delta: Float) {
        _state.update { s -> s.copy(offsetX = s.offsetX + delta) }
    }

    override fun onCleared() {
        cancelAllRenderJobs()
        viewModelScope.launch { port.closeDocument() }
    }

    private fun cancelAllRenderJobs() {
        renderJobs.values.forEach { it.cancel() }
        renderJobs.clear()
    }
}

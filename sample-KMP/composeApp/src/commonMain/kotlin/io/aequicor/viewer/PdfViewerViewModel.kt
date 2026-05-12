package io.aequicor.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aequicor.domain.port.PdfRenderPort
import io.aequicor.domain.usecase.OpenDocumentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfViewerViewModel(
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val port: PdfRenderPort,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    fun openDocument(bytes: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, document = null, renderedPages = emptyMap(), offsetX = 0f) }
            runCatching { openDocumentUseCase(bytes) }
                .onSuccess { doc -> _state.update { it.copy(document = doc, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun ensurePageRendered(pageIndex: Int) {
        val s = _state.value
        if (s.renderedPages.containsKey(pageIndex)) return
        val page = s.document?.pages?.getOrNull(pageIndex) ?: return
        viewModelScope.launch {
            runCatching { port.renderPage(pageIndex, page.size) }
                .onSuccess { bytes ->
                    _state.update { it.copy(renderedPages = it.renderedPages + (pageIndex to bytes)) }
                }
        }
    }

    fun onZoomChange(scaleDelta: Float) {
        _state.update { s ->
            s.copy(zoom = (s.zoom * scaleDelta).coerceIn(ViewerState.MIN_ZOOM, ViewerState.MAX_ZOOM))
        }
    }

    fun onPanX(delta: Float) {
        _state.update { s -> s.copy(offsetX = s.offsetX + delta) }
    }

    override fun onCleared() {
        viewModelScope.launch { port.closeDocument() }
    }
}

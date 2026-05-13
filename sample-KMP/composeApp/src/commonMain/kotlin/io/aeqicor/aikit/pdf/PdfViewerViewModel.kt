package io.aeqicor.aikit.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_RENDER_WIDTH = 1400
private const val ZOOM_MIN = 0.25f
private const val ZOOM_MAX = 4.0f
private const val THUMBNAIL_WIDTH_PX = 120

class PdfViewerViewModel : ViewModel() {
    private var document: PdfDocument? = null

    private val _uiState = MutableStateFlow(PdfViewerState())
    val uiState: StateFlow<PdfViewerState> = _uiState.asStateFlow()

    private val desiredPages = MutableStateFlow<List<Int>>(emptyList())
    private var renderJob: Job? = null

    fun setViewportWidth(width: Int) {
        _uiState.update { it.copy(
            actualViewportWidth = width,
            viewportWidth = minOf(width, MAX_RENDER_WIDTH)
        ) }
    }

    fun setCurrentPage(page: Int) {
        if (_uiState.value.currentPage != page) {
            _uiState.update { it.copy(currentPage = page) }
        }
    }

    fun onScrollHandled() {
        _uiState.update { it.copy(scrollToPage = null) }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    fun toggleSidebar() {
        _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    }

    fun renderThumbnailIfNeeded(pageIndex: Int) {
        if (_uiState.value.thumbnailPages.containsKey(pageIndex)) return
        val doc = document ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = doc.renderPage(pageIndex, THUMBNAIL_WIDTH_PX, 0)
                _uiState.update { state ->
                    state.copy(thumbnailPages = state.thumbnailPages + (pageIndex to bitmap))
                }
            } catch (_: Exception) {}
        }
    }

    // ── Zoom ─────────────────────────────────────────────────────────────────

    fun zoomIn() = applyZoom(_uiState.value.zoomScale * 1.25f)
    fun zoomOut() = applyZoom(_uiState.value.zoomScale * 0.8f)
    fun resetZoom() = applyZoom(1.0f)
    fun fitToWidth() = applyZoom(1.0f)

    fun applyZoom(factor: Float) {
        val newScale = factor.coerceIn(ZOOM_MIN, ZOOM_MAX)
        if (newScale == _uiState.value.zoomScale) return
        _uiState.update { state ->
            state.copy(
                zoomScale = newScale,
                renderedPages = emptyMap(),
                renderToken = state.renderToken + 1
            )
        }
        startRenderConsumer()
    }

    // ── PDF loading ───────────────────────────────────────────────────────────

    fun openPdf(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(isLoading = true, error = null, renderedPages = emptyMap(), thumbnailPages = emptyMap())
            }
            try {
                document?.close()
                val doc = loadPdf(path)
                document = doc
                startRenderConsumer()
                _uiState.update {
                    it.copy(
                        pageCount = doc.pageCount,
                        currentPage = 0,
                        scrollToPage = 0,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun startRenderConsumer() {
        renderJob?.cancel()
        desiredPages.value = emptyList()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            desiredPages.collectLatest { pages ->
                for (pageIndex in pages) {
                    ensureActive()
                    if (_uiState.value.renderedPages.containsKey(pageIndex)) continue
                    val doc = document ?: break
                    val zoom = _uiState.value.zoomScale
                    val viewportW = _uiState.value.viewportWidth.takeIf { it > 0 } ?: 800
                    val w = (viewportW * zoom).toInt().coerceAtLeast(1)
                    try {
                        val bitmap = doc.renderPage(pageIndex, w, 0)
                        _uiState.update { state ->
                            state.copy(renderedPages = state.renderedPages + (pageIndex to bitmap))
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun setDesiredPages(visibleIndices: List<Int>) {
        if (visibleIndices.isEmpty()) return
        val pageCount = _uiState.value.pageCount
        val buffer = visibleIndices.flatMap { idx ->
            (maxOf(0, idx - 2)..minOf(pageCount - 1, idx + 2)).toList()
        }
        val prioritized = (visibleIndices + buffer).distinct()
            .sortedBy { idx -> visibleIndices.minOf { abs(it - idx) } }
        desiredPages.value = prioritized
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun goToPage(pageIndex: Int) {
        val count = _uiState.value.pageCount
        if (count == 0 || pageIndex !in 0 until count) return
        _uiState.update { it.copy(currentPage = pageIndex, scrollToPage = pageIndex) }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun prevPage() = goToPage(_uiState.value.currentPage - 1)

    override fun onCleared() {
        renderJob?.cancel()
        document?.close()
        super.onCleared()
    }
}

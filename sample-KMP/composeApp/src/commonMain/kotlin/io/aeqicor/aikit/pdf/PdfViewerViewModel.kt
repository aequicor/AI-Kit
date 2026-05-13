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

class PdfViewerViewModel : ViewModel() {
    private var document: PdfDocument? = null

    private val _uiState = MutableStateFlow(PdfViewerState())
    val uiState: StateFlow<PdfViewerState> = _uiState.asStateFlow()

    private val desiredPages = MutableStateFlow<List<Int>>(emptyList())
    private var renderJob: Job? = null

    fun setViewportWidth(width: Int) {
        _uiState.update { it.copy(viewportWidth = width) }
    }

    fun setCurrentPage(page: Int) {
        if (_uiState.value.currentPage != page) {
            _uiState.update { it.copy(currentPage = page) }
        }
    }

    fun onScrollHandled() {
        _uiState.update { it.copy(scrollToPage = null) }
    }

    fun openPdf(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, error = null, renderedPages = emptyMap()) }
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
                    val w = _uiState.value.viewportWidth.takeIf { it > 0 } ?: 800
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

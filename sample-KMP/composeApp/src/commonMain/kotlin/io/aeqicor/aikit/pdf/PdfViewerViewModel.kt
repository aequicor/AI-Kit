package io.aeqicor.aikit.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfViewerViewModel : ViewModel() {
    private var document: PdfDocument? = null

    private val _uiState = MutableStateFlow(PdfViewerState())
    val uiState: StateFlow<PdfViewerState> = _uiState.asStateFlow()

    fun setViewportWidth(width: Int) {
        _uiState.update { it.copy(viewportWidth = width) }
    }

    fun openPdf(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                document?.close()
                val doc = loadPdf(path)
                document = doc
                val w = _uiState.value.viewportWidth.takeIf { it > 0 } ?: 800
                val bitmap = doc.renderPage(0, w, 0)
                _uiState.update {
                    it.copy(
                        pageCount = doc.pageCount,
                        currentPage = 0,
                        renderedPage = bitmap,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    override fun onCleared() {
        document?.close()
        super.onCleared()
    }
}

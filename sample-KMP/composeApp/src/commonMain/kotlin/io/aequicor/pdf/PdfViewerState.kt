package io.aequicor.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewMode { SinglePage, ContinuousScroll, TwoPage }

sealed class PdfViewerState {
    data object Idle : PdfViewerState()
    data object Loading : PdfViewerState()
    data class Loaded(val doc: PdfDocument, val currentPage: Int) : PdfViewerState()
    data class Error(val message: String) : PdfViewerState()
}

class PdfViewerController internal constructor(private val scope: CoroutineScope) {
    var state: PdfViewerState by mutableStateOf(PdfViewerState.Idle)
        internal set

    var viewMode: ViewMode by mutableStateOf(ViewMode.SinglePage)

    internal var picker: () -> Unit = {}

    fun openPicker() = picker()

    internal fun loadFrom(bytes: ByteArray) {
        scope.launch {
            state = PdfViewerState.Loading
            state = try {
                val doc = withContext(Dispatchers.Default) { PdfDocument.open(bytes) }
                PdfViewerState.Loaded(doc, 0)
            } catch (e: Exception) {
                PdfViewerState.Error(e.message ?: "Failed to open PDF")
            }
        }
    }

    fun navigateTo(page: Int) {
        val current = state as? PdfViewerState.Loaded ?: return
        if (page in 0 until current.doc.pageCount) {
            state = current.copy(currentPage = page)
        }
    }

    fun close() {
        (state as? PdfViewerState.Loaded)?.doc?.close()
        state = PdfViewerState.Idle
    }
}

@Composable
fun rememberPdfViewerState(): PdfViewerController {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { PdfViewerController(scope) }
    val picker = rememberPdfPicker { bytes ->
        if (bytes != null) controller.loadFrom(bytes)
    }
    SideEffect { controller.picker = picker }
    return controller
}

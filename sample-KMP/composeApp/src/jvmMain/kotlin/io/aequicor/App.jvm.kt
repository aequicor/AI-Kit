package io.aequicor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.aequicor.pdf.JvmFilePickerAdapter
import io.aequicor.viewer.PdfViewerScreen
import io.aequicor.viewer.PdfViewerViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun App() {
    val viewModel: PdfViewerViewModel = koinViewModel()
    val picker: JvmFilePickerAdapter = koinInject()
    val scope = rememberCoroutineScope()
    PdfViewerScreen(
        viewModel = viewModel,
        onOpenFile = {
            scope.launch {
                val bytes = picker.pickPdfFile()
                if (bytes != null) viewModel.openDocument(bytes)
            }
        },
    )
}

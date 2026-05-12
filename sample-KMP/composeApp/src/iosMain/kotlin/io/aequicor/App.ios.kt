package io.aequicor

import androidx.compose.runtime.Composable
import io.aequicor.viewer.PdfViewerScreen
import io.aequicor.viewer.PdfViewerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun App() {
    val viewModel: PdfViewerViewModel = koinViewModel()
    PdfViewerScreen(
        viewModel = viewModel,
        onOpenFile = { /* iOS file picker: wired in MainViewController via IosFilePicker */ },
    )
}

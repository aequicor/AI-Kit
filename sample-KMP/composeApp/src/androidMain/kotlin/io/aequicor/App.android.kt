package io.aequicor

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.aequicor.pdf.rememberPdfFileLauncher
import io.aequicor.viewer.PdfViewerScreen
import io.aequicor.viewer.PdfViewerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun App() {
    val viewModel: PdfViewerViewModel = koinViewModel()
    val context = LocalContext.current
    val launcher = rememberPdfFileLauncher(
        context = context,
        onBytes = { bytes -> viewModel.openDocument(bytes) },
        onError = { /* M1: silent */ },
    )
    PdfViewerScreen(
        viewModel = viewModel,
        onOpenFile = { launcher.launch(arrayOf("application/pdf")) },
    )
}

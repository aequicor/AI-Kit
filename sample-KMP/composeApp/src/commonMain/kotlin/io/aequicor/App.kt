package io.aequicor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.aequicor.pdf.PdfViewerScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        PdfViewerScreen()
    }
}

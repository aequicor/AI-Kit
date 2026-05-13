package io.aeqicor.aikit.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel = viewModel { PdfViewerViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val openAction = rememberOpenPdfAction { path ->
        path?.let { viewModel.openPdf(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Viewer") },
                actions = {
                    Button(onClick = openAction) {
                        Text("Open PDF")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onSizeChanged { size ->
                    if (size.width > 0) viewModel.setViewportWidth(size.width)
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text(
                    text = "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
                state.renderedPage != null -> Image(
                    bitmap = state.renderedPage!!,
                    contentDescription = "Page ${state.currentPage + 1} of ${state.pageCount}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                else -> Text("Open a PDF file to get started")
            }
        }
    }
}

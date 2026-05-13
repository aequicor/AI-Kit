package io.aeqicor.aikit.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel = viewModel { PdfViewerViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val openAction = rememberOpenPdfAction { path ->
        path?.let { viewModel.openPdf(it) }
    }

    // Track current page from scroll position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.setCurrentPage(it) }
    }

    // Push visible indices to render consumer
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibleIndices -> viewModel.setDesiredPages(visibleIndices) }
    }

    // Re-trigger render when zoom changes (cache was cleared, consumer restarted)
    LaunchedEffect(state.renderToken) {
        if (state.renderToken > 0) {
            viewModel.setDesiredPages(listState.layoutInfo.visibleItemsInfo.map { it.index })
        }
    }

    // Handle scroll-to-page signal
    LaunchedEffect(state.scrollToPage) {
        state.scrollToPage?.let { page ->
            listState.animateScrollToItem(page)
            viewModel.onScrollHandled()
        }
    }

    // Pinch-to-zoom (touch / trackpad gesture)
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.applyZoom(state.zoomScale * zoomChange)
    }

    Scaffold(
        topBar = {
            androidx.compose.foundation.layout.Column {
                TopAppBar(
                    title = { Text("PDF Viewer") },
                    actions = {
                        Button(onClick = openAction) { Text("Open") }
                    }
                )
                if (state.pageCount > 0) {
                    PdfNavigationBar(
                        state = state,
                        onPrevPage = { viewModel.prevPage() },
                        onNextPage = { viewModel.nextPage() },
                        onGoToPage = { viewModel.goToPage(it) },
                        onZoomIn = { viewModel.zoomIn() },
                        onZoomOut = { viewModel.zoomOut() },
                        onResetZoom = { viewModel.resetZoom() },
                        onFitToWidth = { viewModel.fitToWidth() }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onSizeChanged { size ->
                    if (size.width > 0) viewModel.setViewportWidth(size.width)
                }
                .transformable(state = transformState),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text(
                    text = "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
                state.pageCount > 0 -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.pageCount) { pageIndex ->
                        val bitmap = state.renderedPages[pageIndex]
                        val density = LocalDensity.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        if (bitmap != null) androidx.compose.ui.unit.Dp.Unspecified
                                        else 400.dp
                                    )
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Page ${pageIndex + 1} of ${state.pageCount}",
                                        modifier = Modifier.width(
                                            with(density) { bitmap.width.toDp() }
                                        ),
                                        contentScale = ContentScale.FillWidth
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
                else -> Text("Open a PDF file to get started")
            }
        }
    }
}

@Composable
private fun PdfNavigationBar(
    state: PdfViewerState,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onFitToWidth: () -> Unit
) {
    var gotoText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Page navigation
        TextButton(onClick = onPrevPage, enabled = state.currentPage > 0) { Text("<") }

        OutlinedTextField(
            value = gotoText,
            onValueChange = { v ->
                if (v.length <= 4 && v.all(Char::isDigit)) gotoText = v
            },
            modifier = Modifier.width(56.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = {
                val page = (gotoText.toIntOrNull() ?: 0) - 1
                if (page in 0 until state.pageCount) onGoToPage(page)
                gotoText = ""
                focusManager.clearFocus()
            }),
            placeholder = { Text("${state.currentPage + 1}") }
        )

        Text(
            text = " / ${state.pageCount}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        TextButton(
            onClick = onNextPage,
            enabled = state.currentPage < state.pageCount - 1
        ) { Text(">") }

        Spacer(modifier = Modifier.weight(1f))

        // Zoom controls
        TextButton(onClick = onZoomOut, enabled = state.zoomScale > 0.25f) { Text("−") }
        Text(
            text = "${(state.zoomScale * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        TextButton(onClick = onZoomIn, enabled = state.zoomScale < 4.0f) { Text("+") }
        TextButton(onClick = onFitToWidth) { Text("Fit") }
    }
}

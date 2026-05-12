package io.aequicor.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.aequicor.domain.model.PdfPageSize
import androidx.compose.runtime.snapshotFlow

@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.onZoomChange(zoomChange)
    }

    val document = state.document
    val pageCount = document?.pageCount ?: 0

    LaunchedEffect(document) {
        if (document == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems: List<LazyListItemInfo> ->
                visibleItems.forEach { item: LazyListItemInfo ->
                    viewModel.ensurePageRendered(item.index)
                    if (item.index > 0) viewModel.ensurePageRendered(item.index - 1)
                    if (item.index < pageCount - 1) viewModel.ensurePageRendered(item.index + 1)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onOpenFile) { Text("Open PDF") }
            if (document != null) {
                Text("$pageCount pages · ${(state.zoom * 100).toInt()}%")
            }
        }

        Box(modifier = Modifier.weight(1f).transformable(transformState)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = "Error: ${state.error}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
                document != null -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(pageCount) { pageIndex ->
                        PdfPageView(
                            pageIndex = pageIndex,
                            pageSize = document.pages[pageIndex].size,
                            renderedBytes = state.renderedPages[pageIndex],
                            zoom = state.zoom,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
                else -> Text(
                    text = "Tap 'Open PDF' to load a document",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun PdfPageView(
    pageIndex: Int,
    pageSize: PdfPageSize,
    renderedBytes: ByteArray?,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = pageSize.widthPx.toFloat() / pageSize.heightPx.toFloat()
    val bitmap = remember(renderedBytes) {
        renderedBytes?.toImageBitmap(pageSize.widthPx, pageSize.heightPx)
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .graphicsLayer(scaleX = zoom, scaleY = zoom)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(text = "${pageIndex + 1}", color = Color.LightGray)
        }
    }
}

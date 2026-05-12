package io.aequicor.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aequicor.domain.model.PdfPageSize

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

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .transformable(transformState)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        viewModel.onPanX(dragAmount)
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll) {
                                val isCtrl = event.keyboardModifiers.isCtrlPressed
                                if (isCtrl) {
                                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (scrollDelta != 0f) {
                                        val zoomFactor = if (scrollDelta < 0) 1.1f else 1f / 1.1f
                                        viewModel.onZoomChange(zoomFactor)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val pageWidthDp: Dp = maxWidth * state.zoom
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }.toInt()

            LaunchedEffect(viewportWidthPx) {
                viewModel.setViewportWidth(viewportWidthPx)
            }

            val viewportPx = viewportWidthPx.toFloat()
            val pagePx = viewportPx * state.zoom
            val overflow = maxOf(0f, pagePx - viewportPx)
            val translationX = (viewportPx - pagePx) / 2f + state.offsetX.coerceIn(-overflow / 2f, overflow / 2f)

            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = "Error: ${state.error}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
                document != null -> Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer(translationX = translationX),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(pageCount) { pageIndex ->
                            val pdfPageSize = document.pages[pageIndex].size
                            val aspectRatio = pdfPageSize.widthPx.toFloat() / pdfPageSize.heightPx.toFloat()

                            DisposableEffect(pageIndex, state.viewportWidthPx) {
                                viewModel.ensurePageRendered(pageIndex)
                                onDispose {
                                    viewModel.cancelPageRender(pageIndex)
                                }
                            }

                            PdfPageView(
                                pageIndex = pageIndex,
                                pageSize = pdfPageSize,
                                renderedPage = state.renderedPages[pageIndex],
                                pageWidth = pageWidthDp,
                                pageHeight = pageWidthDp / aspectRatio,
                            )
                        }
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
    renderedPage: RenderedPage?,
    pageWidth: Dp,
    pageHeight: Dp,
) {
    val bitmap = remember(renderedPage) {
        renderedPage?.let { rp -> rp.bytes.toImageBitmap(rp.width, rp.height) }
    }

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .requiredWidth(pageWidth)
            .height(pageHeight)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Text(text = "${pageIndex + 1}", color = Color.LightGray)
        }
    }
}

package io.aequicor.pdf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.aequicor.pdf.PdfPageImage
import io.aequicor.pdf.PdfRenderer
import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.MAX_SCALE
import io.aequicor.pdf.domain.MIN_SCALE
import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.PdfPage
import io.aequicor.pdf.domain.StrokePoint
import io.aequicor.pdf.presentation.DrawingViewModel
import io.aequicor.pdf.ui.input.StylusEvent
import io.aequicor.pdf.ui.input.stylusInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.max

private fun clampOffset(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    viewportW: Float,
    viewportH: Float,
    contentH: Float,
): Pair<Float, Float> {
    val extraX = max(0f, (viewportW * scale - viewportW) / 2f)
    val cx = offsetX.coerceIn(-extraX, extraX)

    val scaledH = contentH * scale
    val cy = if (scaledH < viewportH) {
        (viewportH - scaledH) / 2f
    } else {
        offsetY.coerceIn(viewportH - scaledH, 0f)
    }
    return cx to cy
}

private fun StylusEvent.toStrokePoint() = StrokePoint(
    x = x, y = y, pressure = pressure, tiltX = tiltX, tiltY = tiltY,
    timestamp = 0L,
)

@Composable
fun PdfViewerScreen() {
    val pdfRenderer: PdfRenderer = koinInject()
    val drawingVM: DrawingViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    var document by remember { mutableStateOf<PdfDocument?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val pendingViewportSize = remember { mutableStateOf(Size.Zero) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    LaunchedEffect(pendingViewportSize.value) {
        delay(64)
        viewportSize = pendingViewportSize.value
    }

    val renderSemaphore = remember { Semaphore(3) }
    val pageCache = remember {
        object : LinkedHashMap<Int, ImageBitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?) =
                size > 5
        }
    }

    // Drawing state
    val layer by drawingVM.layer.collectAsState()
    val activePoints by drawingVM.activePoints.collectAsState()
    var currentTool by remember { mutableStateOf<DrawingTool>(DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L)) }
    var currentDrawingPage by remember { mutableIntStateOf(-1) }

    val filePicker = rememberFilePicker { path ->
        if (path != null) {
            scope.launch {
                val newDoc = withContext(Dispatchers.IO) {
                    document?.let { pdfRenderer.closeDocument(it.id) }
                    pdfRenderer.openDocument(path)
                }
                document = newDoc
                scale = 1f; offsetX = 0f; offsetY = 0f
                currentDrawingPage = -1
                if (newDoc.pageCount > 0) {
                    drawingVM.loadPage(newDoc.id, 0)
                    currentDrawingPage = 0
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        val doc = document
        if (doc != null) {
            DrawingToolbar(
                currentTool = currentTool,
                onBrush = { currentTool = DrawingTool.Brush(widthDp = 4f, color = 0xFF000000L) },
                onEraser = { currentTool = DrawingTool.Eraser(widthDp = 16f) },
                onUndo = { drawingVM.undo() },
                onRedo = { drawingVM.redo() },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (doc != null) {
                val contentHeight by remember(doc) {
                    derivedStateOf {
                        if (viewportSize.width > 0f) {
                            doc.pages.sumOf { page ->
                                viewportSize.width * page.heightPx.toDouble() / page.widthPx
                            }.toFloat()
                        } else 0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { size ->
                            pendingViewportSize.value =
                                Size(size.width.toFloat(), size.height.toFloat())
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                val (cx, cy) = clampOffset(
                                    offsetX + pan.x, offsetY + pan.y,
                                    scale, viewportSize.width, viewportSize.height, contentHeight,
                                )
                                offsetX = cx; offsetY = cy
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Scroll) {
                                        val delta = event.changes.firstOrNull()?.scrollDelta
                                            ?: continue
                                        val (cx, cy) = clampOffset(
                                            offsetX - delta.x * 30f,
                                            offsetY - delta.y * 30f,
                                            scale, viewportSize.width, viewportSize.height, contentHeight,
                                        )
                                        offsetX = cx; offsetY = cy
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .ctrlScrollZoom { factor ->
                            scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                            val (cx, cy) = clampOffset(
                                offsetX, offsetY,
                                scale, viewportSize.width, viewportSize.height, contentHeight,
                            )
                            offsetX = cx; offsetY = cy
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            ),
                    ) {
                        doc.pages.forEach { page ->
                            DrawablePdfPage(
                                pdfRenderer = pdfRenderer,
                                docId = doc.id,
                                page = page,
                                cache = pageCache,
                                semaphore = renderSemaphore,
                                layer = if (currentDrawingPage == page.index) layer
                                        else AnnotationLayer(doc.id, page.index, emptyList()),
                                activePoints = if (currentDrawingPage == page.index) activePoints else null,
                                currentTool = currentTool,
                                onStylusEvent = { event ->
                                    when (event.type) {
                                        StylusEvent.EventType.DOWN -> {
                                            if (currentDrawingPage != page.index) {
                                                currentDrawingPage = page.index
                                                drawingVM.loadPage(doc.id, page.index)
                                            }
                                            drawingVM.beginStroke(currentTool)
                                            drawingVM.addPoint(event.toStrokePoint())
                                        }
                                        StylusEvent.EventType.MOVE ->
                                            drawingVM.addPoint(event.toStrokePoint())
                                        StylusEvent.EventType.UP -> {
                                            drawingVM.addPoint(event.toStrokePoint())
                                            drawingVM.endStroke()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Open a PDF to get started")
                }
            }

            FloatingActionButton(
                onClick = { filePicker() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun DrawingToolbar(
    currentTool: DrawingTool,
    onBrush: () -> Unit,
    onEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isBrush = currentTool is DrawingTool.Brush
            Button(
                onClick = onBrush,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBrush) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isBrush) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(end = 4.dp),
            ) { Text("Brush") }

            Button(
                onClick = onEraser,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isBrush) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (!isBrush) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("Eraser") }

            Box(modifier = Modifier.weight(1f))

            Button(
                onClick = onUndo,
                modifier = Modifier.padding(end = 4.dp),
            ) { Text("Undo") }

            Button(onClick = onRedo) { Text("Redo") }
        }
    }
}

@Composable
private fun DrawablePdfPage(
    pdfRenderer: PdfRenderer,
    docId: PdfDocumentId,
    page: PdfPage,
    cache: MutableMap<Int, ImageBitmap>,
    semaphore: Semaphore,
    layer: AnnotationLayer,
    activePoints: List<StrokePoint>?,
    currentTool: DrawingTool,
    onStylusEvent: (StylusEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(page.widthPx.toFloat() / page.heightPx),
    ) {
        AsyncPdfPageImage(
            pdfRenderer = pdfRenderer,
            docId = docId,
            page = page,
            cache = cache,
            semaphore = semaphore,
        )
        PdfAnnotationOverlay(
            layer = layer,
            activeStroke = activePoints,
            activeTool = currentTool,
            modifier = Modifier
                .fillMaxSize()
                .stylusInput(onStylusEvent),
        )
    }
}

@Composable
private fun AsyncPdfPageImage(
    pdfRenderer: PdfRenderer,
    docId: PdfDocumentId,
    page: PdfPage,
    cache: MutableMap<Int, ImageBitmap>,
    semaphore: Semaphore,
) {
    var bitmap by remember(page.index) { mutableStateOf(cache[page.index]) }

    LaunchedEffect(page.index) {
        if (cache[page.index] == null) {
            val img = semaphore.withPermit {
                val pageImage: PdfPageImage = withContext(Dispatchers.Default) {
                    pdfRenderer.renderPage(docId, page.index, page.widthPx, page.heightPx)
                }
                pageImage.toImageBitmap()
            }
            cache[page.index] = img
            bitmap = img
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "Page ${page.index + 1}",
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray),
        )
    }
}

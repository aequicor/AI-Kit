package io.aequicor.pdf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.aequicor.pdf.PdfPageImage
import io.aequicor.pdf.PdfRenderer
import io.aequicor.pdf.domain.MAX_SCALE
import io.aequicor.pdf.domain.MIN_SCALE
import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.PdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PdfViewerScreen() {
    val pdfRenderer: PdfRenderer = koinInject()
    var document by remember { mutableStateOf<PdfDocument?>(null) }
    val lazyListState = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Shared LRU cache: keeps last 5 rendered bitmaps across the lazy list.
    val pageCache = remember {
        object : LinkedHashMap<Int, ImageBitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?) =
                size > 5
        }
    }

    val filePicker = rememberFilePicker { path ->
        if (path != null) {
            document?.let { pdfRenderer.closeDocument(it.id) }
            document = pdfRenderer.openDocument(path)
            scale = 1f; offsetX = 0f; offsetY = 0f
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val doc = document
        if (doc != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .transformable(transformableState),
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                ) {
                    items(doc.pages, key = { it.index }) { page ->
                        AsyncPdfPageImage(
                            pdfRenderer = pdfRenderer,
                            docId = doc.id,
                            page = page,
                            scale = scale,
                            cache = pageCache,
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

@Composable
private fun AsyncPdfPageImage(
    pdfRenderer: PdfRenderer,
    docId: PdfDocumentId,
    page: PdfPage,
    scale: Float,
    cache: MutableMap<Int, ImageBitmap>,
) {
    var bitmap by remember(page.index) { mutableStateOf(cache[page.index]) }

    LaunchedEffect(page.index) {
        if (cache[page.index] == null) {
            val pageImage: PdfPageImage = withContext(Dispatchers.Default) {
                pdfRenderer.renderPage(docId, page.index, page.widthPx, page.heightPx)
            }
            val img = pageImage.toImageBitmap()
            cache[page.index] = img
            bitmap = img
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "Page ${page.index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(page.widthPx.toFloat() / page.heightPx),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(page.widthPx.toFloat() / page.heightPx)
                .background(Color.LightGray),
        )
    }
}

package io.aequicor.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 5.0f
private const val LRU_CACHE_SIZE = 5

private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
}

@Composable
fun PdfPageCanvas(
    doc: PdfDocument,
    pageIndex: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(doc, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    val cache = remember(doc) { LruCache<Int, ImageBitmap>(LRU_CACHE_SIZE) }

    LaunchedEffect(doc, pageIndex) {
        bitmap = cache[pageIndex] ?: run {
            val rendered = withContext(Dispatchers.Default) { doc.renderPage(pageIndex, 1.5f) }
            cache[pageIndex] = rendered
            rendered
        }
    }

    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .scrollWheelZoom { delta ->
                scale = (scale * (1f + delta * 0.1f)).coerceIn(ZOOM_MIN, ZOOM_MAX)
            },
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
            )
        }
    }
}

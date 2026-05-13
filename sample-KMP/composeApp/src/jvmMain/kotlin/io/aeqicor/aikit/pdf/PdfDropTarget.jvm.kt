package io.aeqicor.aikit.pdf

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.pdfDropTarget(onDrop: (String) -> Unit): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { event ->
        runCatching { event.dragData() is DragData.FilesList }.getOrDefault(false)
    },
    target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            return try {
                val files = (event.dragData() as? DragData.FilesList)?.readFiles()
                    ?: return false
                val path = files
                    .firstOrNull { it.contains(".pdf", ignoreCase = true) }
                    ?.let { runCatching { URI(it).path }.getOrNull() }
                path?.let { onDrop(it) }
                path != null
            } catch (_: Exception) { false }
        }
    }
)

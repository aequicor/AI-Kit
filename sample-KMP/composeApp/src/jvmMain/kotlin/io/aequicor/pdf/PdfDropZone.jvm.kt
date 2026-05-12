package io.aequicor.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
actual fun Modifier.dropPdfFiles(onDrop: (ByteArray) -> Unit): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dropEvent = event.nativeEvent as? DropTargetDropEvent ?: return false
                dropEvent.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    dropEvent.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                }.getOrNull()
                val pdfFile = files
                    ?.filterIsInstance<java.io.File>()
                    ?.firstOrNull { it.name.lowercase().endsWith(".pdf") }
                dropEvent.dropComplete(pdfFile != null)
                if (pdfFile != null) {
                    onDrop(pdfFile.readBytes())
                    return true
                }
                return false
            }
        },
    )

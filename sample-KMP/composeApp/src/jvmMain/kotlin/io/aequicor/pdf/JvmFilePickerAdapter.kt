package io.aequicor.pdf

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File

class JvmFilePickerAdapter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun pickPdfFile(): ByteArray? = withContext(ioDispatcher) {
        val dialog = FileDialog(null as java.awt.Frame?, "Open PDF", FileDialog.LOAD).apply {
            file = "*.pdf"
            isVisible = true
        }
        val dir = dialog.directory ?: return@withContext null
        val file = dialog.file ?: return@withContext null
        File(dir, file).readBytes()
    }
}

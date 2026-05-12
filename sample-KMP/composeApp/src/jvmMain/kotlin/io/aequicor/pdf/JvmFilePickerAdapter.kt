package io.aequicor.pdf

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class JvmFilePickerAdapter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun pickPdfFile(): ByteArray? = withContext(ioDispatcher) {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("PDF files", "pdf")
            dialogTitle = "Open PDF"
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return@withContext null
        chooser.selectedFile?.readBytes()
    }
}

package io.aequicor.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

suspend fun pickPdfFile(): ByteArray? = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("PDF files", "pdf")
        dialogTitle = "Open PDF"
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return@withContext null
    chooser.selectedFile?.readBytes()
}

package io.aequicor.pdf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberFilePicker(onResult: (path: String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("PDF files", "pdf")
                    dialogTitle = "Open PDF"
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
            }
            onResult(path)
        }
    }
}

package io.aequicor.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberPdfPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val onResultState = rememberUpdatedState(onResult)
    return remember(scope) {
        {
            scope.launch {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("PDF files", "pdf")
                }
                val result = withContext(Dispatchers.Main) {
                    chooser.showOpenDialog(null)
                }
                if (result == JFileChooser.APPROVE_OPTION) {
                    val bytes = withContext(Dispatchers.IO) {
                        chooser.selectedFile.readBytes()
                    }
                    onResultState.value(bytes)
                } else {
                    onResultState.value(null)
                }
            }
        }
    }
}

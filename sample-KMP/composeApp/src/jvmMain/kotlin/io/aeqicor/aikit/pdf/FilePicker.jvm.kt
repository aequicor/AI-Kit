package io.aeqicor.aikit.pdf

import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual class FilePicker actual constructor() {
    actual fun pickPdfFile(onResult: (String?) -> Unit) {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                fileFilter = FileNameExtensionFilter("PDF Files", "pdf")
                dialogTitle = "Open PDF"
            }
            val result = chooser.showOpenDialog(null)
            onResult(
                if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath
                else null
            )
        }
    }
}

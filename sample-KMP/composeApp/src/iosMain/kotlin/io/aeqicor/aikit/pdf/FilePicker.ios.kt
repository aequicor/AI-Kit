package io.aeqicor.aikit.pdf

actual class FilePicker actual constructor() {
    actual fun pickPdfFile(onResult: (String?) -> Unit) {
        onResult(null)
    }
}

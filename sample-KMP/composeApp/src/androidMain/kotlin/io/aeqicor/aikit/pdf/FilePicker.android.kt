package io.aeqicor.aikit.pdf

actual class FilePicker actual constructor() {
    actual fun pickPdfFile(onResult: (String?) -> Unit) {
        // Android file picking is handled via rememberLauncherForActivityResult in the Composable layer
    }
}

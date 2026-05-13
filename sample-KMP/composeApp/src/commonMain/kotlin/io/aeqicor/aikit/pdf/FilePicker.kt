package io.aeqicor.aikit.pdf

expect class FilePicker() {
    fun pickPdfFile(onResult: (String?) -> Unit)
}

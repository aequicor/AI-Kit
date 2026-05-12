package io.aequicor.pdf.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(onResult: (path: String?) -> Unit): () -> Unit =
    { throw NotImplementedError("Web FilePicker: use <input type=file> + PDF.js, deferred to M1-web") }

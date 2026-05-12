package io.aequicor.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPdfPicker(onResult: (ByteArray?) -> Unit): () -> Unit =
    remember { { onResult(null) } }

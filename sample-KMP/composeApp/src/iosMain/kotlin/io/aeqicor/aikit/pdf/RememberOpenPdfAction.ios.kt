package io.aeqicor.aikit.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberOpenPdfAction(onResult: (String?) -> Unit): () -> Unit {
    return remember { { onResult(null) } }
}

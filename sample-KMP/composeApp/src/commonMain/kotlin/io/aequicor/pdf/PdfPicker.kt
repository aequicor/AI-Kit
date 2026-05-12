package io.aequicor.pdf

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPdfPicker(onResult: (ByteArray?) -> Unit): () -> Unit

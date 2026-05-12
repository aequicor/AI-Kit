package io.aequicor.pdf

import androidx.compose.ui.Modifier

actual fun Modifier.dropPdfFiles(onDrop: (ByteArray) -> Unit): Modifier = this

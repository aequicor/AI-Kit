package io.aequicor.pdf

import androidx.compose.ui.Modifier

expect fun Modifier.dropPdfFiles(onDrop: (ByteArray) -> Unit): Modifier

package io.aequicor.pdf.ui

import androidx.compose.ui.Modifier

actual fun Modifier.ctrlScrollZoom(onZoom: (factor: Float) -> Unit): Modifier = this

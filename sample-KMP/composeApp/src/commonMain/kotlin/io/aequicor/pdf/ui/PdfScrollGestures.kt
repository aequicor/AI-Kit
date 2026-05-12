package io.aequicor.pdf.ui

import androidx.compose.ui.Modifier

expect fun Modifier.ctrlScrollZoom(onZoom: (factor: Float) -> Unit): Modifier

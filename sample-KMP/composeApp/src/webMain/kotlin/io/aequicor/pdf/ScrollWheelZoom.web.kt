package io.aequicor.pdf

import androidx.compose.ui.Modifier

actual fun Modifier.scrollWheelZoom(onZoom: (delta: Float) -> Unit): Modifier = this

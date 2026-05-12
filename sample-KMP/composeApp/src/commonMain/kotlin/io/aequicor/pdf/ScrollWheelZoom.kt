package io.aequicor.pdf

import androidx.compose.ui.Modifier

expect fun Modifier.scrollWheelZoom(onZoom: (delta: Float) -> Unit): Modifier

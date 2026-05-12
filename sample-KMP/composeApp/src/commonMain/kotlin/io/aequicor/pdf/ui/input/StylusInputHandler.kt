package io.aequicor.pdf.ui.input

import androidx.compose.ui.Modifier

expect fun Modifier.stylusInput(onEvent: (StylusEvent) -> Unit): Modifier

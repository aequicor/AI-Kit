package io.aeqicor.aikit.pdf

import androidx.compose.ui.Modifier

expect fun Modifier.pdfDropTarget(onDrop: (String) -> Unit): Modifier

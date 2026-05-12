package io.aequicor.pdf.ui

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(onResult: (path: String?) -> Unit): () -> Unit

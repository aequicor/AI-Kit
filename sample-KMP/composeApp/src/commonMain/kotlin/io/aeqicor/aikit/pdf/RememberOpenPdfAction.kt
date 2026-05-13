package io.aeqicor.aikit.pdf

import androidx.compose.runtime.Composable

@Composable
expect fun rememberOpenPdfAction(onResult: (String?) -> Unit): () -> Unit

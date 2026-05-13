package io.aeqicor.aikit.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberOpenPdfAction(onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onResult(uri?.toString())
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}

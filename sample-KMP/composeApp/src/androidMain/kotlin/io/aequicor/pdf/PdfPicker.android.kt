package io.aequicor.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPdfPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val onResultState = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            onResultState.value(bytes)
        } else {
            onResultState.value(null)
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}

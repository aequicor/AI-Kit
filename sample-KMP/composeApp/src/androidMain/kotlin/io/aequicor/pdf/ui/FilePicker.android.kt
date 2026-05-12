package io.aequicor.pdf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberFilePicker(onResult: (path: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        // SAF returns a content URI; copy to cache so PdfRenderer can use File(path).
        val cacheFile = File(context.cacheDir, "picked_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { input.copyTo(it) }
        }
        onResult(cacheFile.absolutePath)
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

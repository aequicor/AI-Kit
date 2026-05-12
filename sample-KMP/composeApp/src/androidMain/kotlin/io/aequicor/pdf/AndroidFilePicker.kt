package io.aequicor.pdf

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberPdfFileLauncher(
    context: Context,
    scope: CoroutineScope = rememberCoroutineScope(),
    onBytes: suspend (ByteArray) -> Unit,
    onError: (Throwable) -> Unit,
): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            }.fold(
                onSuccess = { bytes -> withContext(Dispatchers.Main) { onBytes(bytes) } },
                onFailure = { onError(it) },
            )
        }
    }

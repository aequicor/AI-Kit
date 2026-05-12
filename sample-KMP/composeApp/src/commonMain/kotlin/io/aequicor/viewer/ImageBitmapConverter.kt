package io.aequicor.viewer

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.toImageBitmap(width: Int, height: Int): ImageBitmap

package io.aequicor.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo

actual fun ByteArray.toImageBitmap(width: Int, height: Int): ImageBitmap =
    SkiaImage.makeRaster(
        imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
        bytes = this,
        rowBytes = width * 4,
    ).toComposeImageBitmap()

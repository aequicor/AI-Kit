package io.aequicor.pdf.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.aequicor.pdf.PdfPageImage
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.jetbrains.compose.ui.tooling.preview.Preview

// iOS PdfRenderer captures BGRA premultiplied bytes from CGImage context.
actual fun PdfPageImage.toImageBitmap(): ImageBitmap {
    val info = ImageInfo(
        width = widthPx,
        height = heightPx,
        colorType = ColorType.BGRA_8888,
        colorAlphaType = ColorAlphaType.PREMUL,
    )
    return SkiaImage.makeRaster(info, pixels, widthPx * 4L).toComposeImageBitmap()
}

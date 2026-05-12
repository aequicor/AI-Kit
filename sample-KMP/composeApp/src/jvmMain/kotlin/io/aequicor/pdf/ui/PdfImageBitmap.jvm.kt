package io.aequicor.pdf.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.aequicor.pdf.PdfPageImage
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

actual fun PdfPageImage.toImageBitmap(): ImageBitmap {
    val intArray = IntArray(widthPx * heightPx)
    ByteBuffer.wrap(pixels).asIntBuffer().get(intArray)
    val img = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, widthPx, heightPx, intArray, 0, widthPx)
    return img.toComposeImageBitmap()
}

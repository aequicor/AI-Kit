package io.aequicor.pdf.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.aequicor.pdf.PdfPageImage
import java.nio.ByteBuffer

actual fun PdfPageImage.toImageBitmap(): ImageBitmap {
    val intArray = IntArray(widthPx * heightPx)
    ByteBuffer.wrap(pixels).asIntBuffer().get(intArray)
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    bmp.setPixels(intArray, 0, widthPx, 0, 0, widthPx, heightPx)
    return bmp.asImageBitmap()
}

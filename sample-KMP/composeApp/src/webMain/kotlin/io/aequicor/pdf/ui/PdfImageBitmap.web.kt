package io.aequicor.pdf.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.aequicor.pdf.PdfPageImage

actual fun PdfPageImage.toImageBitmap(): ImageBitmap =
    throw NotImplementedError("Web: PDF.js rendering deferred to M1-web")

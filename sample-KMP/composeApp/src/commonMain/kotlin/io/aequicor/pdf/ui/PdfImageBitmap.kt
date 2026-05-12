package io.aequicor.pdf.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.aequicor.pdf.PdfPageImage

expect fun PdfPageImage.toImageBitmap(): ImageBitmap

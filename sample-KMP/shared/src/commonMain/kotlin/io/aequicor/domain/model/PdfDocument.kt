package io.aequicor.domain.model

data class PdfDocument(
    val pageCount: Int,
    val pages: List<PdfPage>,
)

package io.aequicor.pdf.domain

data class AnnotationLayer(
    val documentId: PdfDocumentId,
    val pageIndex: Int,
    val strokes: List<Stroke>,
)

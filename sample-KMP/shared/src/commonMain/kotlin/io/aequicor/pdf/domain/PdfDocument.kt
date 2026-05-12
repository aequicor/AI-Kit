package io.aequicor.pdf.domain

data class PdfDocument(val id: PdfDocumentId, val pageCount: Int, val pages: List<PdfPage>)

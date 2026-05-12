package io.aequicor.pdf.domain.repository

import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.PdfDocumentId

interface AnnotationRepository {
    suspend fun getLayer(docId: PdfDocumentId, page: Int): AnnotationLayer
    suspend fun saveLayer(layer: AnnotationLayer)
}

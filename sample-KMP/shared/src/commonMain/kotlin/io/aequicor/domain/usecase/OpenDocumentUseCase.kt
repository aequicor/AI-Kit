package io.aequicor.domain.usecase

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.port.PdfRenderPort

class OpenDocumentUseCase(private val port: PdfRenderPort) {
    suspend operator fun invoke(bytes: ByteArray): PdfDocument = port.openDocument(bytes)
}

package io.aequicor.domain.usecase

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.port.PdfRenderPort

/**
 * Opens a PDF document from raw bytes and returns its structure.
 *
 * Delegates to [PdfRenderPort.openDocument]; the document remains open
 * until [PdfRenderPort.closeDocument] is called by the caller.
 */
class OpenDocumentUseCase(private val port: PdfRenderPort) {
    suspend operator fun invoke(bytes: ByteArray): PdfDocument = port.openDocument(bytes)
}

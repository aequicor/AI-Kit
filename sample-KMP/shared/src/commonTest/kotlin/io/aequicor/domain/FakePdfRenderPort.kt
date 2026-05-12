package io.aequicor.domain

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.port.PdfRenderPort

class FakePdfRenderPort(
    private val pageCount: Int = 3,
    private val pageWidth: Int = 100,
    private val pageHeight: Int = 200,
) : PdfRenderPort {

    var closeCalled = false

    override suspend fun openDocument(bytes: ByteArray): PdfDocument {
        val pages = (0 until pageCount).map { i ->
            PdfPage(index = i, size = PdfPageSize(pageWidth, pageHeight))
        }
        return PdfDocument(pageCount = pageCount, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): ByteArray =
        ByteArray(targetSize.widthPx * targetSize.heightPx * 4)

    override suspend fun closeDocument() {
        closeCalled = true
    }
}

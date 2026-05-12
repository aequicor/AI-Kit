package io.aequicor.domain

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.model.RenderedPage
import io.aequicor.domain.port.PdfRenderPort

class FakePdfRenderPort(
    private val pageCount: Int = 3,
    private val pageWidth: Int = 100,
    private val pageHeight: Int = 200,
) : PdfRenderPort {

    private companion object {
        const val ARGB_BYTES_PER_PIXEL = 4
    }

    var closeCalled = false

    override suspend fun openDocument(bytes: ByteArray): PdfDocument {
        val pages = (0 until pageCount).map { i ->
            PdfPage(index = i, size = PdfPageSize(pageWidth, pageHeight))
        }
        return PdfDocument(pageCount = pageCount, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): RenderedPage =
        RenderedPage(
            bytes = ByteArray(targetSize.widthPx * targetSize.heightPx * ARGB_BYTES_PER_PIXEL),
            width = targetSize.widthPx,
            height = targetSize.heightPx,
        )

    override suspend fun closeDocument() {
        closeCalled = true
    }
}

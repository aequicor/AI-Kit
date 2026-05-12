package io.aequicor.domain

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfModelTest {

    @Test
    fun pdfPageSize_storesWidthAndHeight() {
        val size = PdfPageSize(widthPx = 595, heightPx = 842)
        assertEquals(595, size.widthPx)
        assertEquals(842, size.heightPx)
    }

    @Test
    fun pdfPage_storesIndexAndSize() {
        val size = PdfPageSize(100, 200)
        val page = PdfPage(index = 0, size = size)
        assertEquals(0, page.index)
        assertEquals(size, page.size)
    }

    @Test
    fun pdfDocument_storesPageCountAndPages() {
        val pages = listOf(
            PdfPage(0, PdfPageSize(100, 200)),
            PdfPage(1, PdfPageSize(100, 200)),
        )
        val doc = PdfDocument(pageCount = 2, pages = pages)
        assertEquals(2, doc.pageCount)
        assertEquals(2, doc.pages.size)
    }
}

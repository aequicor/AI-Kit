package io.aequicor.domain

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfModelTest {

    private companion object {
        const val A4_WIDTH_PX = 595
        const val A4_HEIGHT_PX = 842
        const val TEST_PAGE_WIDTH_PX = 100
        const val TEST_PAGE_HEIGHT_PX = 200
    }

    @Test
    fun pdfPageSize_storesWidthAndHeight() {
        val size = PdfPageSize(widthPx = A4_WIDTH_PX, heightPx = A4_HEIGHT_PX)
        assertEquals(A4_WIDTH_PX, size.widthPx)
        assertEquals(A4_HEIGHT_PX, size.heightPx)
    }

    @Test
    fun pdfPage_storesIndexAndSize() {
        val size = PdfPageSize(TEST_PAGE_WIDTH_PX, TEST_PAGE_HEIGHT_PX)
        val page = PdfPage(index = 0, size = size)
        assertEquals(0, page.index)
        assertEquals(size, page.size)
    }

    @Test
    fun pdfDocument_storesPageCountAndPages() {
        val pages = listOf(
            PdfPage(0, PdfPageSize(TEST_PAGE_WIDTH_PX, TEST_PAGE_HEIGHT_PX)),
            PdfPage(1, PdfPageSize(TEST_PAGE_WIDTH_PX, TEST_PAGE_HEIGHT_PX)),
        )
        val doc = PdfDocument(pageCount = 2, pages = pages)
        assertEquals(2, doc.pageCount)
        assertEquals(2, doc.pages.size)
    }
}

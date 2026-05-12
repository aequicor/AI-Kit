package io.aequicor.pdf.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfDocumentTest {

    private fun makeDoc(pageCount: Int = 3) = PdfDocument(
        id = PdfDocumentId("test.pdf"),
        pageCount = pageCount,
        pages = (0 until pageCount).map { PdfPage(index = it, widthPx = 595, heightPx = 842) },
    )

    @Test
    fun pageCountMatchesPagesSize() {
        val doc = makeDoc(5)
        assertEquals(5, doc.pageCount)
        assertEquals(5, doc.pages.size)
    }

    @Test
    fun pageIndexIsCorrect() {
        val doc = makeDoc(3)
        doc.pages.forEachIndexed { i, page -> assertEquals(i, page.index) }
    }

    @Test
    fun pageLookupByIndex() {
        val doc = makeDoc(4)
        val page = doc.pages.firstOrNull { it.index == 2 }
        assertEquals(2, page?.index)
        assertEquals(595, page?.widthPx)
        assertEquals(842, page?.heightPx)
    }

    @Test
    fun missingPageReturnsNull() {
        val doc = makeDoc(2)
        assertNull(doc.pages.firstOrNull { it.index == 99 })
    }

    @Test
    fun idValuePreserved() {
        val doc = makeDoc()
        assertEquals("test.pdf", doc.id.value)
    }

    @Test
    fun equalDocumentsAreEqual() {
        val a = makeDoc(2)
        val b = makeDoc(2)
        assertEquals(a, b)
    }
}

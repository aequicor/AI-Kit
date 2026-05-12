package io.aequicor.pdf

import io.aequicor.pdf.domain.PdfDocumentId
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfRendererJvmTest {

    private lateinit var samplePdf: File
    private lateinit var renderer: PdfRenderer

    @Before
    fun setUp() {
        // Generate a minimal 2-page PDF programmatically so no binary fixture is needed.
        samplePdf = File.createTempFile("sample_test", ".pdf").also { file ->
            PDDocument().use { doc ->
                doc.addPage(PDPage(PDRectangle(595f, 842f)))   // A4
                doc.addPage(PDPage(PDRectangle(612f, 792f)))   // Letter
                doc.save(file)
            }
        }
        renderer = PdfRenderer()
    }

    @After
    fun tearDown() {
        samplePdf.delete()
    }

    @Test
    fun openDocumentReturnsCorrectPageCount() {
        val doc = renderer.openDocument(samplePdf.absolutePath)
        assertEquals(2, doc.pageCount)
        assertEquals(2, doc.pages.size)
        renderer.closeDocument(doc.id)
    }

    @Test
    fun openDocumentPageDimensionsAreNonZero() {
        val doc = renderer.openDocument(samplePdf.absolutePath)
        doc.pages.forEach { page ->
            assertTrue(page.widthPx > 0, "Page ${page.index} widthPx must be > 0")
            assertTrue(page.heightPx > 0, "Page ${page.index} heightPx must be > 0")
        }
        renderer.closeDocument(doc.id)
    }

    @Test
    fun renderPageProducesNonNullBitmapWithCorrectSize() {
        val doc = renderer.openDocument(samplePdf.absolutePath)
        val targetW = 300
        val targetH = 424
        val image = renderer.renderPage(doc.id, 0, targetW, targetH)
        assertNotNull(image)
        // Actual rendered size may differ slightly from target (PDFBox scales to DPI),
        // but pixels array must have exactly widthPx * heightPx * 4 bytes.
        assertEquals(image.widthPx * image.heightPx * 4, image.pixels.size)
        renderer.closeDocument(doc.id)
    }

    @Test
    fun renderPagePixelsAreNotAllZero() {
        val doc = renderer.openDocument(samplePdf.absolutePath)
        val image = renderer.renderPage(doc.id, 0, 200, 283)
        assertTrue(image.pixels.any { it != 0.toByte() }, "Rendered pixels must not all be zero")
        renderer.closeDocument(doc.id)
    }

    @Test
    fun closeDocumentAllowsReopeningTheSameFile() {
        val doc = renderer.openDocument(samplePdf.absolutePath)
        renderer.closeDocument(doc.id)
        val doc2 = renderer.openDocument(samplePdf.absolutePath)
        assertEquals(2, doc2.pageCount)
        renderer.closeDocument(doc2.id)
    }
}

package io.aequicor.pdf

import io.aequicor.domain.model.PdfDocument
import io.aequicor.domain.model.PdfPage
import io.aequicor.domain.model.PdfPageSize
import io.aequicor.domain.model.RenderedPage
import io.aequicor.domain.port.PdfRenderPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.kCGImageAlphaPremultipliedFirst
import platform.Foundation.NSData
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage

@OptIn(ExperimentalForeignApi::class)
class IosPdfRenderAdapter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PdfRenderPort {

    private companion object {
        const val ARGB_BYTES_PER_PIXEL = 4
        const val BITS_PER_COMPONENT = 8L
    }

    private var pdfDocument: PDFDocument? = null

    override suspend fun openDocument(bytes: ByteArray): PdfDocument = withContext(ioDispatcher) {
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val doc = PDFDocument(data = nsData)
        pdfDocument = doc

        val pageCount = doc.pageCount.toInt()
        val pages = (0 until pageCount).map { i ->
            val page: PDFPage = doc.pageAtIndex(i.toULong())!!
            val bounds = page.boundsForBox(platform.PDFKit.kPDFDisplayBoxMediaBox)
            PdfPage(
                index = i,
                size = PdfPageSize(
                    widthPx = bounds.size.width.toInt(),
                    heightPx = bounds.size.height.toInt(),
                ),
            )
        }
        PdfDocument(pageCount = pageCount, pages = pages)
    }

    override suspend fun renderPage(pageIndex: Int, targetSize: PdfPageSize): RenderedPage =
        withContext(ioDispatcher) {
            val doc = checkNotNull(pdfDocument) { "No document open" }
            val page: PDFPage = doc.pageAtIndex(pageIndex.toULong())!!

            val w = targetSize.widthPx.toLong()
            val h = targetSize.heightPx.toLong()
            val bytesPerRow = w * ARGB_BYTES_PER_PIXEL
            val totalBytes = (bytesPerRow * h).toInt()

            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                data = null,
                width = w.toULong(),
                height = h.toULong(),
                bitsPerComponent = BITS_PER_COMPONENT.toULong(),
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = kCGImageAlphaPremultipliedFirst,
            )
            checkNotNull(context) { "Failed to create CGBitmapContext" }

            try {
                val bounds = page.boundsForBox(platform.PDFKit.kPDFDisplayBoxMediaBox)
                val scaleX = targetSize.widthPx / bounds.size.width
                val scaleY = targetSize.heightPx / bounds.size.height
                platform.CoreGraphics.CGContextScaleCTM(context, scaleX, scaleY)
                page.drawWithBox(platform.PDFKit.kPDFDisplayBoxMediaBox, toContext = context)

                val rawPtr = CGBitmapContextGetData(context)
                    ?: return@withContext RenderedPage(ByteArray(totalBytes), targetSize.widthPx, targetSize.heightPx)
                val bytes = ByteArray(totalBytes) { i -> (rawPtr.reinterpret<kotlinx.cinterop.ByteVar>()[i]) }
                RenderedPage(bytes, targetSize.widthPx, targetSize.heightPx)
            } finally {
                CGContextRelease(context)
                CGColorSpaceRelease(colorSpace)
            }
        }

    override suspend fun closeDocument() {
        pdfDocument = null
    }
}

package io.aequicor.pdf

import io.aequicor.pdf.domain.PdfDocument
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.PdfPage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFDisplayBox
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext

@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer actual constructor() {

    private val openDocs = mutableMapOf<String, PDFDocument>()

    actual fun openDocument(path: String): PdfDocument {
        val url = NSURL.fileURLWithPath(path)
        val pdDoc = PDFDocument(url) ?: error("Cannot open PDF at $path")
        val id = PdfDocumentId(path)
        openDocs[path] = pdDoc
        val pageCount = pdDoc.pageCount.toInt()
        val pages = (0 until pageCount).map { i ->
            val page = pdDoc.pageAtIndex(i.toULong()) ?: error("Cannot read page $i")
            val bounds = page.boundsForBox(PDFDisplayBox.PDFDisplayBoxMediaBox)
            PdfPage(
                index = i,
                widthPx = bounds.size.width.toInt(),
                heightPx = bounds.size.height.toInt(),
            )
        }
        return PdfDocument(id = id, pageCount = pageCount, pages = pages)
    }

    actual fun renderPage(
        docId: PdfDocumentId,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageImage {
        val pdDoc = openDocs[docId.value] ?: error("Document not open: ${docId.value}")
        val page = pdDoc.pageAtIndex(pageIndex.toULong()) ?: error("Cannot read page $pageIndex")

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(widthPx.toDouble(), heightPx.toDouble()), true, 1.0)
        val ctx = UIGraphicsGetCurrentContext() ?: error("No graphics context")

        // Flip coordinate system: PDF origin is bottom-left, UIKit is top-left.
        platform.CoreGraphics.CGContextTranslateCTM(ctx, 0.0, heightPx.toDouble())
        platform.CoreGraphics.CGContextScaleCTM(ctx, 1.0, -1.0)

        val bounds = page.boundsForBox(PDFDisplayBox.PDFDisplayBoxMediaBox)
        val scaleX = widthPx / bounds.size.width
        val scaleY = heightPx / bounds.size.height
        platform.CoreGraphics.CGContextScaleCTM(ctx, scaleX, scaleY)

        page.drawWithBox(PDFDisplayBox.PDFDisplayBoxMediaBox)

        val uiImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        val cgImage = uiImage?.CGImage ?: error("Failed to get CGImage")
        val dataRef = platform.CoreGraphics.CGDataProviderCopyData(
            platform.CoreGraphics.CGImageGetDataProvider(cgImage)
        ) ?: error("Failed to copy CGImage data")
        val length = platform.CoreFoundation.CFDataGetLength(dataRef).toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            platform.CoreFoundation.CFDataGetBytes(
                dataRef,
                platform.CoreFoundation.CFRangeMake(0, length.toLong()),
                pinned.addressOf(0).reinterpret(),
            )
        }
        platform.CoreFoundation.CFRelease(dataRef)
        return PdfPageImage(widthPx = widthPx, heightPx = heightPx, pixels = bytes)
    }

    actual fun closeDocument(docId: PdfDocumentId) {
        openDocs.remove(docId.value)
    }
}

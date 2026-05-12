package io.aequicor.pdf

/** Platform-agnostic ARGB_8888 pixel buffer (row-major, 4 bytes per pixel). */
data class PdfPageImage(val widthPx: Int, val heightPx: Int, val pixels: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfPageImage) return false
        return widthPx == other.widthPx && heightPx == other.heightPx && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

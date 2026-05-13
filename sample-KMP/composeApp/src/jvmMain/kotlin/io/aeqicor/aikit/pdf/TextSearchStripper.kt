package io.aeqicor.aikit.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

internal class TextSearchStripper : PDFTextStripper() {
    private val _matches = mutableListOf<SearchMatch>()
    private var query = ""
    private var pgWidth = 1f
    private var pgHeight = 1f
    private val pageChars = mutableListOf<Pair<Char, TextPosition>>()

    fun search(document: PDDocument, searchQuery: String): List<SearchMatch> {
        if (searchQuery.isBlank()) return emptyList()
        query = searchQuery.lowercase()
        _matches.clear()
        sortByPosition = true
        getText(document)
        return _matches.toList()
    }

    override fun processPage(page: PDPage) {
        pgWidth = page.mediaBox.width
        pgHeight = page.mediaBox.height
        pageChars.clear()
        super.processPage(page)
        findMatchesInPage()
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        text.forEachIndexed { i, c ->
            if (i < textPositions.size) pageChars += c to textPositions[i]
        }
        super.writeString(text, textPositions)
    }

    private fun findMatchesInPage() {
        val pageIdx = currentPageNo - 1
        val fullText = pageChars.map { it.first }.joinToString("").lowercase()
        var from = 0
        while (from <= fullText.length - query.length) {
            val hit = fullText.indexOf(query, from)
            if (hit < 0) break

            val charPositions = pageChars.subList(hit, hit + query.length).map { it.second }
            if (charPositions.isNotEmpty()) {
                val xLeft  = charPositions.minOf { it.xDirAdj }
                val xRight = charPositions.maxOf { it.xDirAdj + it.widthDirAdj }
                val yBase  = charPositions.minOf { it.yDirAdj }
                val charH  = charPositions.maxOf { it.heightDir }

                // PDF origin: bottom-left, Y up → Compose origin: top-left, Y down
                // plan formula: composeY = pageH - pdfY - wordH
                val normLeft   = (xLeft / pgWidth).coerceIn(0f, 1f)
                val normRight  = (xRight / pgWidth).coerceIn(0f, 1f)
                val normTop    = ((pgHeight - yBase - charH) / pgHeight).coerceIn(0f, 1f)
                val normBottom = ((pgHeight - yBase) / pgHeight).coerceIn(0f, 1f)

                _matches += SearchMatch(
                    pageIndex = pageIdx,
                    text = pageChars.subList(hit, hit + query.length).map { it.first }.joinToString(""),
                    left = normLeft, top = normTop,
                    right = normRight, bottom = normBottom,
                    hasCoords = true
                )
            }
            from = hit + 1
        }
    }
}

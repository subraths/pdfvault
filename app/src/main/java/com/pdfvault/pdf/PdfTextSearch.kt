package com.pdfvault.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** A match rectangle in normalized page coordinates (0..1, origin top-left). */
data class HighlightRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A page that contains the search term, with a snippet and the match rectangles to highlight. */
data class PageMatch(
    val pageIndex: Int,
    val snippet: String,
    val count: Int,
    val rects: List<HighlightRect> = emptyList(),
)

/**
 * Full-text search across a PDF using PdfBox (Android's PdfRenderer can't read text).
 * Returns the pages containing [query] with a snippet and per-page hit count. Extraction
 * is cooperative-cancellable, so a superseded search stops promptly. Capped at [maxResults].
 */
suspend fun searchPdfText(
    file: File,
    query: String,
    maxResults: Int = 300,
): List<PageMatch> = withContext(Dispatchers.IO) {
    val needle = query.trim()
    if (needle.isEmpty()) return@withContext emptyList()
    runCatching {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            val results = ArrayList<PageMatch>()
            for (page in 1..doc.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(doc)
                val count = countOccurrences(text, needle)
                if (count > 0) {
                    results += PageMatch(page - 1, snippetAround(text, needle), count)
                    if (results.size >= maxResults) break
                }
            }
            results as List<PageMatch>
        }
    }.getOrDefault(emptyList())
}

/**
 * Full-text search that also returns per-match highlight rectangles (in normalized page
 * coordinates), by capturing each glyph's [TextPosition] via a custom stripper. Runs in a
 * single pass over the document. Rectangles are approximate: a match spanning a line break is
 * merged into one box, and rotated pages aren't corrected.
 */
suspend fun searchPdfHighlights(
    file: File,
    query: String,
    maxResults: Int = 300,
): List<PageMatch> = withContext(Dispatchers.IO) {
    val needle = query.trim()
    if (needle.isEmpty()) return@withContext emptyList()
    runCatching {
        PDDocument.load(file).use { doc ->
            val stripper = HighlightStripper(needle, maxResults)
            stripper.startPage = 1
            stripper.endPage = doc.numberOfPages
            stripper.getText(doc) // drives startPage/writeString/endPage; text result ignored
            stripper.results.toList()
        }
    }.getOrDefault(emptyList())
}

/**
 * Extracts the plain text of a single [pageIndex] (0-based) for the "copy text" sheet. Returns an
 * empty string if the page has no extractable text or can't be read.
 */
suspend fun extractPageText(file: File, pageIndex: Int): String = withContext(Dispatchers.IO) {
    runCatching {
        PDDocument.load(file).use { doc ->
            if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return@use ""
            val stripper = PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }
            stripper.getText(doc).trim()
        }
    }.getOrDefault("")
}

/** Accumulates glyph positions per page and computes match rectangles in [endPage]. */
private class HighlightStripper(
    private val needle: String,
    private val maxResults: Int,
) : PDFTextStripper() {

    val results = ArrayList<PageMatch>()

    private val text = StringBuilder()
    private val charPositions = ArrayList<TextPosition>()
    private var pageWidth = 1f
    private var pageHeight = 1f

    init {
        sortByPosition = true
    }

    override fun startPage(page: PDPage) {
        text.setLength(0)
        charPositions.clear()
        val box = page.cropBox
        pageWidth = box.width.takeIf { it > 0 } ?: 1f
        pageHeight = box.height.takeIf { it > 0 } ?: 1f
        super.startPage(page)
    }

    override fun writeString(string: String, textPositions: List<TextPosition>) {
        // Rebuild the text from positions so every character maps back to a glyph box.
        for (tp in textPositions) {
            val unicode = tp.unicode ?: continue
            for (c in unicode) {
                text.append(c)
                charPositions.add(tp)
            }
        }
    }

    override fun endPage(page: PDPage) {
        if (results.size < maxResults) {
            val haystack = text.toString()
            var idx = haystack.indexOf(needle, 0, ignoreCase = true)
            var count = 0
            val rects = ArrayList<HighlightRect>()
            while (idx >= 0) {
                count++
                val glyphs = (idx until idx + needle.length).mapNotNull { charPositions.getOrNull(it) }
                unionRect(glyphs)?.let(rects::add)
                idx = haystack.indexOf(needle, idx + needle.length, ignoreCase = true)
            }
            if (count > 0) {
                results.add(PageMatch(currentPageNo - 1, snippetAround(haystack, needle), count, rects))
            }
        }
        super.endPage(page)
    }

    private fun unionRect(glyphs: List<TextPosition>): HighlightRect? {
        if (glyphs.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minTop = Float.MAX_VALUE
        var maxRight = -Float.MAX_VALUE
        var maxBottom = -Float.MAX_VALUE
        for (tp in glyphs) {
            val left = tp.xDirAdj
            val bottom = tp.yDirAdj
            val top = bottom - tp.heightDir
            val right = left + tp.widthDirAdj
            if (left < minX) minX = left
            if (right > maxRight) maxRight = right
            if (top < minTop) minTop = top
            if (bottom > maxBottom) maxBottom = bottom
        }
        return HighlightRect(
            left = (minX / pageWidth).coerceIn(0f, 1f),
            top = (minTop / pageHeight).coerceIn(0f, 1f),
            right = (maxRight / pageWidth).coerceIn(0f, 1f),
            bottom = (maxBottom / pageHeight).coerceIn(0f, 1f),
        )
    }
}

private fun countOccurrences(haystack: String, needle: String): Int {
    var count = 0
    var idx = haystack.indexOf(needle, 0, ignoreCase = true)
    while (idx >= 0) {
        count++
        idx = haystack.indexOf(needle, idx + needle.length, ignoreCase = true)
    }
    return count
}

private val whitespace = Regex("\\s+")

private fun snippetAround(text: String, query: String): String {
    val collapsed = text.replace(whitespace, " ").trim()
    val i = collapsed.indexOf(query, ignoreCase = true)
    if (i < 0) return collapsed.take(80)
    val start = (i - 30).coerceAtLeast(0)
    val end = (i + query.length + 50).coerceAtMost(collapsed.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < collapsed.length) "…" else ""
    return prefix + collapsed.substring(start, end) + suffix
}

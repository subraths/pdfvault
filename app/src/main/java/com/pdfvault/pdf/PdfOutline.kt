package com.pdfvault.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One node of a PDF's table of contents (bookmark outline). Top-level entries are
 * "chapters"; their [children] are sections (which may nest further).
 */
data class TocEntry(
    val id: Int,
    val title: String,
    val pageIndex: Int?,
    val children: List<TocEntry>,
)

/**
 * Reads the document outline with PdfBox. Android's built-in PdfRenderer can render pages
 * but cannot expose bookmarks, so we parse the outline separately here. Returns an empty
 * list if the PDF has no outline or it cannot be read.
 */
suspend fun loadOutline(file: File): List<TocEntry> = withContext(Dispatchers.IO) {
    runCatching {
        PDDocument.load(file).use { doc -> extractOutline(doc) }
    }.getOrDefault(emptyList())
}

/** Outline extraction against an already-open [doc], so one parse can serve several readers. */
internal fun extractOutline(doc: PDDocument): List<TocEntry> {
    val outline = doc.documentCatalog?.documentOutline ?: return emptyList()
    val counter = intArrayOf(0)
    return convert(outline.children(), doc, counter)
}

private fun convert(items: Iterable<PDOutlineItem>, doc: PDDocument, counter: IntArray): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    for (item in items) {
        val id = counter[0]++
        val pageIndex = runCatching {
            val page = item.findDestinationPage(doc)
            if (page != null) doc.pages.indexOf(page).takeIf { it >= 0 } else null
        }.getOrNull()
        val children = convert(item.children(), doc, counter)
        result += TocEntry(
            id = id,
            title = item.title?.trim().orEmpty().ifEmpty { "Untitled" },
            pageIndex = pageIndex,
            children = children,
        )
    }
    return result
}

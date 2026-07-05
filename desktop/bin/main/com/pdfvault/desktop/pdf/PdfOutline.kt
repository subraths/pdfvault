package com.pdfvault.desktop.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
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

/** Reads the document outline with PDFBox. Returns an empty list if there is none or it can't be read. */
suspend fun loadOutline(file: File): List<TocEntry> = withContext(Dispatchers.IO) {
    runCatching {
        PDDocument.load(file).use { doc ->
            val outline = doc.documentCatalog?.documentOutline ?: return@use emptyList()
            val counter = intArrayOf(0)
            convert(outline.children(), doc, counter)
        }
    }.getOrDefault(emptyList())
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

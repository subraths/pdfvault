package com.pdfvault.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Navigation extras parsed from a PDF with PdfBox: bookmark outline + tappable link regions. */
data class PdfExtras(
    val outline: List<TocEntry> = emptyList(),
    val links: Map<Int, List<PdfLink>> = emptyMap(),
)

/**
 * Parses the outline and link annotations in a single PdfBox pass. The reader previously loaded
 * the document twice (once per feature) right at open, doubling CPU/allocation work exactly when
 * the first pages are rendering. Parsing is capped to [maxMainMemoryBytes] of heap — larger
 * documents spill to temp files instead of driving the app towards OOM.
 */
suspend fun loadPdfExtras(
    file: File,
    maxMainMemoryBytes: Long = 32L * 1024 * 1024,
): PdfExtras = withContext(Dispatchers.IO) {
    runCatching {
        PDDocument.load(file, MemoryUsageSetting.setupMixed(maxMainMemoryBytes)).use { doc ->
            PdfExtras(
                outline = runCatching { extractOutline(doc) }.getOrDefault(emptyList()),
                links = runCatching { extractLinks(doc) }.getOrDefault(emptyMap()),
            )
        }
    }.getOrDefault(PdfExtras())
}

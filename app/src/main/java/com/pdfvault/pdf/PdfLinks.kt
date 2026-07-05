package com.pdfvault.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where a tapped link goes: an external URL, or another page within the document. */
sealed interface LinkTarget {
    data class Web(val uri: String) : LinkTarget
    data class Page(val index: Int) : LinkTarget
}

/** A tappable link region on a page, positioned in normalized page coordinates (0..1, top-left). */
data class PdfLink(
    val rect: HighlightRect,
    val target: LinkTarget,
)

/**
 * Extracts tappable link annotations for every page using PdfBox (Android's PdfRenderer exposes
 * no annotations). Rectangles are normalized to the crop box, top-left origin — the same space
 * as [HighlightRect] — so the reader can overlay them directly. Page rotation isn't corrected.
 * Returns a page-index → links map; pages without links are omitted.
 */
suspend fun loadPageLinks(file: File): Map<Int, List<PdfLink>> = withContext(Dispatchers.IO) {
    runCatching {
        PDDocument.load(file).use { doc -> extractLinks(doc) }
    }.getOrDefault(emptyMap())
}

/** Link extraction against an already-open [doc], so one parse can serve several readers. */
internal fun extractLinks(doc: PDDocument): Map<Int, List<PdfLink>> {
    val out = HashMap<Int, List<PdfLink>>()
    doc.pages.forEachIndexed { pageIndex, page ->
        val box = page.cropBox
        val originX = box.lowerLeftX
        val originY = box.lowerLeftY
        val width = box.width.takeIf { it > 0f } ?: 1f
        val height = box.height.takeIf { it > 0f } ?: 1f

        val links = ArrayList<PdfLink>()
        val annotations = runCatching { page.annotations }.getOrNull().orEmpty()
        for (annotation in annotations) {
            if (annotation !is PDAnnotationLink) continue
            val rect = annotation.rectangle ?: continue
            val target = runCatching { resolveTarget(doc, annotation) }.getOrNull() ?: continue

            // PDF user space has a bottom-left origin; flip Y to our top-left space.
            val left = ((rect.lowerLeftX - originX) / width)
            val right = ((rect.upperRightX - originX) / width)
            val top = ((originY + height - rect.upperRightY) / height)
            val bottom = ((originY + height - rect.lowerLeftY) / height)
            links += PdfLink(
                rect = HighlightRect(
                    left = minOf(left, right).coerceIn(0f, 1f),
                    top = minOf(top, bottom).coerceIn(0f, 1f),
                    right = maxOf(left, right).coerceIn(0f, 1f),
                    bottom = maxOf(top, bottom).coerceIn(0f, 1f),
                ),
                target = target,
            )
        }
        if (links.isNotEmpty()) out[pageIndex] = links
    }
    return out
}

/** Resolves a link's action or direct destination to an external URL or an in-document page. */
private fun resolveTarget(doc: PDDocument, link: PDAnnotationLink): LinkTarget? {
    when (val action = link.action) {
        is PDActionURI -> action.uri?.takeIf { it.isNotBlank() }?.let { return LinkTarget.Web(it) }
        is PDActionGoTo -> resolvePage(doc, action.destination)?.let { return LinkTarget.Page(it) }
    }
    // Some links carry a destination directly rather than via an action.
    return resolvePage(doc, link.destination)?.let { LinkTarget.Page(it) }
}

private fun resolvePage(doc: PDDocument, destination: PDDestination?): Int? {
    val pageDest: PDPageDestination = when (destination) {
        is PDPageDestination -> destination
        is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(destination) ?: return null
        else -> return null
    }
    val byNumber = runCatching { pageDest.retrievePageNumber() }.getOrDefault(-1)
    if (byNumber >= 0) return byNumber
    val page = pageDest.page ?: return null
    return doc.pages.indexOf(page).takeIf { it >= 0 }
}

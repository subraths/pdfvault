package com.pdfvault.desktop.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

private val thumbnailCache = HashMap<String, ImageBitmap>()

/**
 * Renders the first page of a locally-cached PDF to a small [targetWidthPx] thumbnail for the
 * recents list. Results are memoized by path so re-scrolling the list is cheap. Returns null if
 * the file is missing or can't be read.
 */
suspend fun renderPdfThumbnail(file: File, targetWidthPx: Int = 150): ImageBitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || file.length() == 0L) return@withContext null
    val cacheKey = "${file.path}:$targetWidthPx"
    synchronized(thumbnailCache) { thumbnailCache[cacheKey] }?.let { return@withContext it }
    runCatching {
        PDDocument.load(file).use { doc ->
            if (doc.numberOfPages == 0) return@use null
            val pointWidth = doc.getPage(0).cropBox.width.takeIf { it > 0f } ?: 612f
            val dpi = (targetWidthPx * 72f / pointWidth).coerceIn(12f, 200f)
            PDFRenderer(doc).renderImageWithDPI(0, dpi, ImageType.RGB).toComposeImageBitmap()
        }
    }.getOrNull()?.also { bitmap ->
        synchronized(thumbnailCache) { thumbnailCache[cacheKey] = bitmap }
    }
}

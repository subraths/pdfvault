package com.pdfvault.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// PdfRenderer is not safe to use from multiple instances concurrently, so serialise thumbnails.
private val thumbnailLock = Mutex()

/**
 * Renders the first page of a local PDF to a small [targetWidthPx] bitmap for use as a
 * file-list thumbnail. Returns null if the file can't be opened or has no pages. Cheap and
 * cancellation-friendly; intended only for PDFs already cached on-device.
 */
suspend fun renderPdfThumbnail(file: File, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || file.length() == 0L) return@withContext null
    thumbnailLock.withLock {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(0).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }
}

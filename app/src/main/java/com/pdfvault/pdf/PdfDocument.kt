package com.pdfvault.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Thin wrapper over the platform [PdfRenderer]. PdfRenderer allows only one open page
 * at a time and is not thread-safe, so all access is serialised through a [Mutex].
 */
class PdfDocument(file: File) : Closeable {

    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /** Cheap width/height ratio for [index] (opens the page but renders nothing). */
    suspend fun pageAspectRatio(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                if (page.height > 0) page.width.toFloat() / page.height else 0.707f
            }
        }
    }

    /** Renders [index] to a bitmap scaled to [targetWidthPx], preserving aspect ratio. */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val scale = targetWidthPx.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}

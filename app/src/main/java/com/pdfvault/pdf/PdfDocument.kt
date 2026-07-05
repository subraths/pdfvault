package com.pdfvault.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Thin wrapper over the platform [PdfRenderer]. PdfRenderer allows only one open page
 * at a time and is not thread-safe, so all access is serialised through a [Mutex].
 *
 * Lifecycle safety: [close] never races an in-flight render — it marks the document closed
 * (so new work fails fast with a benign exception the callers already catch) and then closes
 * the native renderer *under the same mutex*, off the caller's thread. Closing mid-render
 * used to crash the app with "Already closed" / native aborts when leaving the reader.
 */
class PdfDocument(file: File) : Closeable {

    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()

    @Volatile
    private var closed = false

    // Aspect ratios are immutable per page; caching them avoids re-opening pages (which takes
    // the render mutex) every time a page scrolls back into view.
    private val aspectCache = ConcurrentHashMap<Int, Float>()

    /** Snapshotted at open so it stays readable (for UI) even after [close]. */
    val pageCount: Int = renderer.pageCount

    /** Cheap width/height ratio for [index] (opens the page but renders nothing). */
    suspend fun pageAspectRatio(index: Int): Float {
        aspectCache[index]?.let { return it }
        return mutex.withLock {
            aspectCache[index]?.let { return@withLock it }
            check(!closed) { "PDF document is closed" }
            withContext(Dispatchers.IO) {
                renderer.openPage(index).use { page ->
                    val aspect = if (page.height > 0) page.width.toFloat() / page.height else 0.707f
                    aspectCache[index] = aspect
                    aspect
                }
            }
        }
    }

    /**
     * Renders [index] to a bitmap scaled to [targetWidthPx], preserving aspect ratio. The result
     * may be smaller than requested: dimensions are capped (long side and total pixels) so a very
     * tall page can't demand a 100MB+ allocation or exceed GPU texture limits, and an
     * [OutOfMemoryError] clears the page cache and retries once at half size instead of crashing.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = mutex.withLock {
        check(!closed) { "PDF document is closed" }
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                if (page.height > 0) aspectCache[index] = page.width.toFloat() / page.height
                val (width, height) = cappedSize(page.width, page.height, targetWidthPx)
                try {
                    renderToBitmap(page, width, height)
                } catch (oom: OutOfMemoryError) {
                    PageRenderCache.clear()
                    renderToBitmap(page, (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1))
                }
            }
        }
    }

    private fun renderToBitmap(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    override fun close() {
        if (closed) return
        closed = true
        // Wait for any in-flight render to finish before closing the native handles, without
        // blocking the caller (onCleared runs on the main thread).
        closeScope.launch {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
            }
        }
    }

    private companion object {
        /** Long-side cap: stays under common GPU max-texture sizes. */
        const val MAX_DIMENSION_PX = 4096

        /** Total-pixel budget per page bitmap (~48MB at ARGB_8888). */
        const val MAX_PIXELS = 12_000_000L

        val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun cappedSize(pageWidth: Int, pageHeight: Int, targetWidthPx: Int): Pair<Int, Int> {
            val aspect = if (pageHeight > 0) pageWidth.toFloat() / pageHeight else 0.707f
            var w = targetWidthPx.coerceAtLeast(1)
            var h = (w / aspect).roundToInt().coerceAtLeast(1)
            if (h > MAX_DIMENSION_PX) {
                h = MAX_DIMENSION_PX
                w = (h * aspect).roundToInt().coerceAtLeast(1)
            }
            if (w > MAX_DIMENSION_PX) {
                w = MAX_DIMENSION_PX
                h = (w / aspect).roundToInt().coerceAtLeast(1)
            }
            val pixels = w.toLong() * h
            if (pixels > MAX_PIXELS) {
                val scale = sqrt(MAX_PIXELS.toDouble() / pixels)
                w = (w * scale).roundToInt().coerceAtLeast(1)
                h = (h * scale).roundToInt().coerceAtLeast(1)
            }
            return w to h
        }
    }
}

package com.pdfvault.desktop.pdf

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Process-lifetime, byte-capped LRU of rendered page bitmaps so scrolling a page back into view
 * (or zooming back to a prior width) is instant instead of re-rendering from the PDF. Keyed by
 * "<docId>:<page>:<widthPx>" — a different render width is a different entry.
 *
 * android.util.LruCache isn't available on the JVM, so this is a small synchronized
 * access-ordered LinkedHashMap that evicts oldest entries once total pixel bytes exceed the cap.
 */
object PageRenderCache {
    private const val MAX_BYTES = 192L * 1024 * 1024

    private var bytes = 0L
    // accessOrder = true so get() moves an entry to the most-recently-used end.
    private val map = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)

    private fun sizeOf(bitmap: ImageBitmap): Long = bitmap.width.toLong() * bitmap.height.toLong() * 4L

    fun key(docId: String, page: Int, widthPx: Int, rotation: Int = 0) = "$docId:$page:$widthPx:$rotation"

    @Synchronized
    fun get(key: String): ImageBitmap? = map[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        val previous = map.put(key, bitmap)
        if (previous != null) bytes -= sizeOf(previous)
        bytes += sizeOf(bitmap)
        val it = map.entries.iterator()
        while (bytes > MAX_BYTES && map.size > 1) {
            val eldest = it.next()
            bytes -= sizeOf(eldest.value)
            it.remove()
        }
    }
}

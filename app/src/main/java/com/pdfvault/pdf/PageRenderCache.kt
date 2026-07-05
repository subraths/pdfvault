package com.pdfvault.pdf

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-lifetime LRU of freshly-rendered (clean, un-highlighted) page bitmaps, so scrolling a
 * page back into view is instant instead of re-rendering it from the PDF. Byte-capped to bound
 * memory. Keyed by "<docId>:<page>:<widthPx>" — a different zoom width is a different entry.
 *
 * Sized to hold the prefetch window plus a couple of higher-resolution (zoomed) pages, scaled
 * down on low-RAM devices (heap class is a good proxy for device class even though bitmap pixel
 * data lives in native memory on API 26+).
 */
object PageRenderCache {
    private val maxBytes: Int = run {
        val budget = Runtime.getRuntime().maxMemory() / 3
        budget.coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024).toInt()
    }

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun key(docId: String, page: Int, widthPx: Int) = "$docId:$page:$widthPx"

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)

    /** Emergency valve: drops every cached bitmap (used on [OutOfMemoryError] recovery). */
    fun clear() = cache.evictAll()
}

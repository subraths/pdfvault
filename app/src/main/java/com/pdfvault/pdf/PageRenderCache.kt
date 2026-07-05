package com.pdfvault.pdf

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-lifetime LRU of freshly-rendered (clean, un-highlighted) page bitmaps, so scrolling a
 * page back into view is instant instead of re-rendering it from the PDF. Byte-capped to bound
 * memory. Keyed by "<docId>:<page>:<widthPx>" — a different zoom width is a different entry.
 *
 * Sized to hold the prefetch window plus a couple of higher-resolution (zoomed) pages. Bitmap
 * pixel data lives in native memory on API 26+, so this is bounded by device RAM, not the heap.
 */
object PageRenderCache {
    private val cache = object : LruCache<String, Bitmap>(128 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun key(docId: String, page: Int, widthPx: Int) = "$docId:$page:$widthPx"

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
}

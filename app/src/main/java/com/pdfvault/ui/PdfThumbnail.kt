package com.pdfvault.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pdfvault.pdf.renderPdfThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Process-lifetime memory cache of rendered PDF thumbnails, keyed by "<path>:<widthPx>".
 * Lets a thumbnail paint on the first frame (no placeholder flash) once it has been rendered
 * once — so re-entering a list or scrolling a row back into view is instant.
 */
private object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
}

/**
 * On-disk PNG cache under cacheDir/thumbs, so thumbnails survive process death: after a restart
 * the first paint decodes a tiny PNG instead of re-rendering the whole PDF.
 */
private object ThumbnailDiskCache {
    private fun fileFor(context: Context, key: String): File {
        val name = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        return File(dir, "$name.png")
    }

    fun get(context: Context, key: String): Bitmap? = runCatching {
        val file = fileFor(context, key)
        if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()

    fun put(context: Context, key: String, bitmap: Bitmap) {
        runCatching {
            fileFor(context, key).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
    }
}

/**
 * First-page preview of a locally-cached PDF, for use as a list leading icon. Serves cached
 * bitmaps (memory → disk) before rendering, and only renders (off the main thread) on a full
 * miss; the slot keeps a fixed size so rows never shift when the image arrives.
 */
@Composable
fun PdfThumbnail(
    file: File,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    height: Dp = 52.dp,
) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { (width.toPx() * 2).toInt() }
    // The cache path already encodes the object's ETag, so path+width uniquely identifies a render.
    val key = "${file.path}:$widthPx"

    val bitmap by produceState(ThumbnailCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                ThumbnailDiskCache.get(context, key)
                    ?: renderPdfThumbnail(file, widthPx)?.also { ThumbnailDiskCache.put(context, key, it) }
            }?.also { ThumbnailCache.put(key, it) }
        }
    }

    Box(
        modifier = modifier.size(width = width, height = height).clip(RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val rendered = bitmap
        if (rendered != null) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            )
        }
    }
}

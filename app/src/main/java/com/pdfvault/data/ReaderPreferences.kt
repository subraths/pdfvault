package com.pdfvault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which way pages advance in the reader. */
enum class ReadingDirection { VERTICAL, HORIZONTAL }

/** Color treatment applied to rendered pages for comfortable reading. */
enum class ReadingMode { NORMAL, NIGHT, SEPIA }

/**
 * Small, non-secret reader settings persisted in plain SharedPreferences: the preferred
 * scroll direction (global) and the last page read per document (so reading resumes).
 */
@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    var readingDirection: ReadingDirection
        get() = runCatching {
            ReadingDirection.valueOf(prefs.getString(KEY_DIRECTION, null) ?: ReadingDirection.VERTICAL.name)
        }.getOrDefault(ReadingDirection.VERTICAL)
        set(value) {
            prefs.edit().putString(KEY_DIRECTION, value.name).apply()
        }

    /** When true, vertical mode scrolls continuously (all pages flow) instead of page-by-page. */
    var verticalContinuous: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()
        }

    /** The global default reading mode, used for documents the user hasn't overridden. */
    var readingMode: ReadingMode
        get() = runCatching {
            ReadingMode.valueOf(prefs.getString(KEY_MODE, null) ?: ReadingMode.NORMAL.name)
        }.getOrDefault(ReadingMode.NORMAL)
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    /** Reading mode remembered for a specific document, falling back to the global default. */
    fun readingMode(objectKey: String): ReadingMode = runCatching {
        ReadingMode.valueOf(prefs.getString(modeKey(objectKey), null) ?: readingMode.name)
    }.getOrDefault(readingMode)

    /** Persists a per-document reading mode and adopts it as the new global default. */
    fun setReadingMode(objectKey: String, mode: ReadingMode) {
        prefs.edit()
            .putString(modeKey(objectKey), mode.name)
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /** In-reader screen brightness 0f..1f, or -1f to follow the system setting. */
    var readingBrightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, -1f)
        set(value) {
            prefs.edit().putFloat(KEY_BRIGHTNESS, value).apply()
        }

    fun lastPage(objectKey: String): Int = prefs.getInt(pageKey(objectKey), 0)

    fun setLastPage(objectKey: String, page: Int) {
        prefs.edit()
            .putInt(pageKey(objectKey), page)
            .putLong(pageTsKey(objectKey), System.currentTimeMillis())
            .apply()
    }

    /** When the reading position for [objectKey] last changed (epoch millis), for sync ordering. */
    fun lastPageUpdatedAt(objectKey: String): Long = prefs.getLong(pageTsKey(objectKey), 0L)

    /** Adopts a reading position from a backend sync, keeping the server's change time. */
    fun setLastPageFromSync(objectKey: String, page: Int, updatedAt: Long) {
        prefs.edit()
            .putInt(pageKey(objectKey), page)
            .putLong(pageTsKey(objectKey), updatedAt)
            .apply()
    }

    /** Bookmarked 0-based page indices for a document. */
    fun bookmarks(objectKey: String): Set<Int> =
        prefs.getString(bookmarkKey(objectKey), null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setBookmarks(objectKey: String, pages: Set<Int>) {
        prefs.edit().putString(bookmarkKey(objectKey), pages.sorted().joinToString(",")).apply()
    }

    private fun pageKey(objectKey: String): String = "page::$objectKey"

    private fun pageTsKey(objectKey: String): String = "pagets::$objectKey"

    private fun bookmarkKey(objectKey: String): String = "bm::$objectKey"

    private fun modeKey(objectKey: String): String = "mode::$objectKey"

    private companion object {
        const val KEY_DIRECTION = "reading_direction"
        const val KEY_CONTINUOUS = "vertical_continuous"
        const val KEY_MODE = "reading_mode"
        const val KEY_BRIGHTNESS = "reading_brightness"
    }
}

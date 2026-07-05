package com.pdfvault.desktop.data

import java.io.File
import java.util.Properties

/** Color treatment applied to rendered pages for comfortable reading. */
enum class ReadingMode { NORMAL, NIGHT, SEPIA }

/**
 * Small, non-secret reader settings persisted to a plain properties file under the config dir:
 * the last page read per document (so reading resumes), the reading mode (global default plus a
 * per-document override), and per-document bookmarks.
 */
object ReaderPreferences {
    private val file = File(AppStorage.configDir, "reader.properties")
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    private fun save() {
        file.outputStream().use { props.store(it, "PdfVault reader preferences") }
    }

    fun lastPage(objectKey: String): Int = props.getProperty(pageKey(objectKey))?.toIntOrNull() ?: 0

    fun setLastPage(objectKey: String, page: Int) {
        props.setProperty(pageKey(objectKey), page.toString())
        save()
    }

    /** The global default reading mode, for documents the user hasn't overridden. */
    var readingMode: ReadingMode
        get() = runCatching { ReadingMode.valueOf(props.getProperty(KEY_MODE) ?: "NORMAL") }
            .getOrDefault(ReadingMode.NORMAL)
        set(value) {
            props.setProperty(KEY_MODE, value.name)
            save()
        }

    /** Reading mode remembered for a specific document, falling back to the global default. */
    fun readingMode(objectKey: String): ReadingMode = runCatching {
        ReadingMode.valueOf(props.getProperty(modeKey(objectKey)) ?: readingMode.name)
    }.getOrDefault(readingMode)

    /** Persists a per-document reading mode and adopts it as the new global default. */
    fun setReadingMode(objectKey: String, mode: ReadingMode) {
        props.setProperty(modeKey(objectKey), mode.name)
        props.setProperty(KEY_MODE, mode.name)
        save()
    }

    fun bookmarks(objectKey: String): Set<Int> =
        props.getProperty(bookmarkKey(objectKey))
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setBookmarks(objectKey: String, pages: Set<Int>) {
        props.setProperty(bookmarkKey(objectKey), pages.sorted().joinToString(","))
        save()
    }

    private fun pageKey(objectKey: String) = "page::$objectKey"
    private fun modeKey(objectKey: String) = "mode::$objectKey"
    private fun bookmarkKey(objectKey: String) = "bm::$objectKey"

    private const val KEY_MODE = "reading_mode"
}

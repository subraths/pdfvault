package com.pdfvault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A PDF the user has opened, for the "Recently opened" tab. */
data class RecentItem(
    val objectKey: String,
    val name: String,
    val openedAtMillis: Long,
    /** Total page count captured when opened, for a progress bar. 0 = unknown. */
    val totalPages: Int = 0,
)

/**
 * Persists the most-recently-opened PDFs (by S3 key) as a small MRU list in plain
 * SharedPreferences. Exposes the list as a [StateFlow] so the Recents tab updates live.
 */
@Singleton
class RecentsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("recents", Context.MODE_PRIVATE)

    private val _recents = MutableStateFlow(load())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    /** Records [objectKey] as just-opened, moving it to the front and de-duplicating. */
    fun record(objectKey: String, totalPages: Int = 0) {
        if (objectKey.isBlank()) return
        val name = objectKey.substringAfterLast('/').ifBlank { objectKey }
        val entry = RecentItem(objectKey, name, System.currentTimeMillis(), totalPages)
        val updated = (listOf(entry) + _recents.value.filterNot { it.objectKey == objectKey })
            .take(MAX_ENTRIES)
        persist(updated)
    }

    fun remove(objectKey: String) =
        persist(_recents.value.filterNot { it.objectKey == objectKey })

    /** Replaces the whole list (used to adopt the merged result of a backend sync). */
    fun replaceAll(items: List<RecentItem>) =
        persist(items.sortedByDescending { it.openedAtMillis }.take(MAX_ENTRIES))

    /** Re-inserts a removed [item] at its original position (by open time), for undo. */
    fun restore(item: RecentItem) {
        val updated = (_recents.value.filterNot { it.objectKey == item.objectKey } + item)
            .sortedByDescending { it.openedAtMillis }
            .take(MAX_ENTRIES)
        persist(updated)
    }

    fun clear() = persist(emptyList())

    private fun persist(list: List<RecentItem>) {
        _recents.value = list
        // One line per entry: "<openedAtMillis>\t<totalPages>\t<objectKey>". Keys never contain a tab.
        val encoded = list.joinToString("\n") { "${it.openedAtMillis}\t${it.totalPages}\t${it.objectKey}" }
        prefs.edit().putString(KEY_ITEMS, encoded).apply()
    }

    private fun load(): List<RecentItem> {
        val raw = prefs.getString(KEY_ITEMS, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            // Newer entries have 3 fields; tolerate the older 2-field ("millis\tkey") format.
            val parts = line.split("\t", limit = 3)
            val millis = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val (totalPages, key) = if (parts.size >= 3) {
                (parts[1].toIntOrNull() ?: 0) to parts[2]
            } else {
                0 to (parts.getOrNull(1) ?: "")
            }
            if (key.isBlank()) return@mapNotNull null
            RecentItem(key, key.substringAfterLast('/').ifBlank { key }, millis, totalPages)
        }
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val MAX_ENTRIES = 50
    }
}

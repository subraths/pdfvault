package com.pdfvault.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** A PDF the user has opened, for the "Recently opened" list. */
data class RecentItem(
    val objectKey: String,
    val name: String,
    val openedAtMillis: Long,
    /** Total page count captured when opened, for a progress bar. 0 = unknown. */
    val totalPages: Int = 0,
)

/**
 * Persists the most-recently-opened PDFs (by S3 key) as a small MRU list under the config dir.
 * Exposes the list as a [StateFlow] so the Recents screen updates live.
 */
object RecentsStore {
    private const val MAX_ENTRIES = 50
    private val file = File(AppStorage.configDir, "recents.tsv")

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
        val encoded = list.joinToString("\n") { "${it.openedAtMillis}\t${it.totalPages}\t${it.objectKey}" }
        runCatching { file.writeText(encoded) }
    }

    private fun load(): List<RecentItem> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t", limit = 3)
            val millis = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val totalPages = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val key = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RecentItem(key, key.substringAfterLast('/').ifBlank { key }, millis, totalPages)
        }
    }
}

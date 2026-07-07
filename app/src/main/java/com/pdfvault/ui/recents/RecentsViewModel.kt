package com.pdfvault.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfvault.S3SessionManager
import com.pdfvault.data.ReaderPreferences
import com.pdfvault.data.RecentItem
import com.pdfvault.data.RecentsStore
import com.pdfvault.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val recents: RecentsStore,
    private val session: S3SessionManager,
    private val readerPrefs: ReaderPreferences,
    private val sync: SyncManager,
) : ViewModel() {

    val items: StateFlow<List<RecentItem>> = recents.recents

    /** Removes locally and tombstones in the cloud, so the item disappears on every device. */
    fun remove(objectKey: String) {
        recents.remove(objectKey)
        sync.deleteRecentRemote(objectKey)
    }

    /** Restores a swiped-away [item] (undo) and revives it in the cloud. */
    fun restore(item: RecentItem) {
        recents.restore(item)
        sync.pushRecent(item.objectKey)
    }

    fun clearAll() {
        val keys = recents.recents.value.map { it.objectKey }
        recents.clear()
        keys.forEach { sync.deleteRecentRemote(it) }
    }

    /** Pulls the latest recents from the backend (no-op when signed out / not configured). */
    fun syncFromCloud() {
        viewModelScope.launch { sync.syncAll() }
    }

    /** Last page the user reached in [objectKey] (0-based), for a reading-progress bar. */
    fun lastPage(objectKey: String): Int = readerPrefs.lastPage(objectKey)

    /**
     * The on-device cached copy of a recent PDF (for a list thumbnail), or null if it's no
     * longer cached. Recents don't carry an ETag, so this resolves the newest cached version.
     */
    fun localPdf(objectKey: String): File? = session.cachedPdf(objectKey, eTag = null)
}

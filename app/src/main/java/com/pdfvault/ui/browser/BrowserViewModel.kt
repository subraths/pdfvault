package com.pdfvault.ui.browser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfvault.S3SessionManager
import com.pdfvault.data.model.S3Item
import com.pdfvault.transfer.Transfer
import com.pdfvault.transfer.TransferProgress
import com.pdfvault.transfer.Transfers
import com.pdfvault.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration

/** How the file list is ordered. Folders always sort first (by name). */
enum class SortMode { NAME, DATE, SIZE }

/** Search either the current folder (local filter) or the whole bucket (recursive). */
enum class SearchScope { FOLDER, BUCKET }

data class BrowserUiState(
    val bucket: String = "",
    val path: String = "",
    val items: List<S3Item> = emptyList(),
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val canGoUp: Boolean = false,
    val error: String? = null,
    // Items being moved; non-empty puts the browser in "pick a destination" mode.
    val movingItems: List<S3Item> = emptyList(),
    val query: String = "",
    val sortMode: SortMode = SortMode.NAME,
    val sortAscending: Boolean = true,
    val searchScope: SearchScope = SearchScope.FOLDER,
    // Multi-select: `selecting` shows the selection app bar; `selected` holds picked item keys.
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    // Live progress of a background upload/download, or null when idle.
    val transfer: TransferProgress? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val session: S3SessionManager,
    private val transfers: Transfers,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val repo get() = session.repository

    // S3 has no folders; we track navigation as a stack of key prefixes ("" == root).
    private val prefixStack = ArrayDeque<String>().apply { addLast("") }
    private val currentPrefix: String get() = prefixStack.last()

    private val _state = MutableStateFlow(BrowserUiState(bucket = repo?.bucket.orEmpty()))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    // The unfiltered/unsorted listing of the current folder; the visible list is derived from it.
    private var rawItems: List<S3Item> = emptyList()

    // Items hidden pending an undoable delete; committed (actually removed from S3) or restored.
    private var pendingDeletion: List<S3Item> = emptyList()
    private val _deletedEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val deletedEvent: SharedFlow<Int> = _deletedEvent.asSharedFlow()

    // Whole-bucket file listing, cached for bucket-wide search; invalidated on refresh/switch.
    private var allFilesCache: List<S3Item.File>? = null

    init {
        // React to account add/switch/remove: reset to the new bucket's root and reload.
        viewModelScope.launch {
            session.activeProfile.collect {
                prefixStack.clear()
                prefixStack.addLast("")
                allFilesCache = null
                pendingDeletion = emptyList()
                _state.update {
                    it.copy(
                        query = "",
                        searchScope = SearchScope.FOLDER,
                        selecting = false,
                        selected = emptySet(),
                        movingItems = emptyList(),
                    )
                }
                refresh()
            }
        }
        // Mirror background-transfer progress into state; re-list when a batch finishes.
        viewModelScope.launch {
            var wasTransferring = false
            transfers.progress.collect { progress ->
                _state.update { it.copy(transfer = progress) }
                if (wasTransferring && progress == null) refresh()
                wasTransferring = progress != null
            }
        }
    }

    fun refresh() {
        allFilesCache = null
        if (_state.value.searchScope == SearchScope.BUCKET) {
            ensureAllFilesLoaded()
            return
        }
        val repository = repo ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.listChildren(currentPrefix) }
                .onSuccess { items ->
                    rawItems = items
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = viewOf(items, it.query, it.sortMode, it.sortAscending),
                            path = currentPrefix,
                            canGoUp = prefixStack.size > 1,
                            bucket = repository.bucket,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.userMessage()) } }
        }
    }

    /** Switches search between the current folder and the whole bucket. */
    fun toggleSearchScope() {
        val next = if (_state.value.searchScope == SearchScope.FOLDER) SearchScope.BUCKET else SearchScope.FOLDER
        _state.update { it.copy(searchScope = next) }
        if (next == SearchScope.BUCKET) ensureAllFilesLoaded() else recomputeItems()
    }

    /** Exits search, returning to the current folder view. */
    fun exitSearch() {
        _state.update { it.copy(query = "", searchScope = SearchScope.FOLDER) }
        recomputeItems()
    }

    private fun ensureAllFilesLoaded() {
        if (allFilesCache != null) {
            recomputeItems()
            return
        }
        val repository = repo ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, items = emptyList()) }
            runCatching { repository.listAllFiles() }
                .onSuccess { files ->
                    allFilesCache = files
                    _state.update { it.copy(isLoading = false) }
                    recomputeItems()
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.userMessage()) } }
        }
    }

    private fun recomputeItems() = _state.update {
        val items = if (it.searchScope == SearchScope.BUCKET) {
            bucketView(allFilesCache.orEmpty(), it.query, it.sortMode, it.sortAscending)
        } else {
            viewOf(rawItems, it.query, it.sortMode, it.sortAscending)
        }
        it.copy(items = items)
    }

    fun openFolder(folder: S3Item.Folder) {
        prefixStack.addLast(folder.key)
        _state.update { it.copy(query = "") } // a fresh folder starts unfiltered
        refresh()
    }

    /** Returns true if a parent folder was popped; false when already at the root. */
    fun goUp(): Boolean {
        if (prefixStack.size <= 1) return false
        prefixStack.removeLast()
        _state.update { it.copy(query = "") }
        refresh()
        return true
    }

    /** Jumps to an ancestor [prefix] (a breadcrumb segment); "" is the bucket root. */
    fun navigateTo(prefix: String) {
        if (currentPrefix == prefix) return
        while (prefixStack.size > 1 && prefixStack.last() != prefix) prefixStack.removeLast()
        _state.update { it.copy(query = "") }
        refresh()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        if (_state.value.searchScope == SearchScope.BUCKET && allFilesCache == null) {
            ensureAllFilesLoaded()
        } else {
            recomputeItems()
        }
    }

    /** Selects a sort field, toggling ascending/descending when the same field is chosen again. */
    fun setSort(mode: SortMode) {
        _state.update {
            val ascending = if (it.sortMode == mode) !it.sortAscending else true
            it.copy(sortMode = mode, sortAscending = ascending)
        }
        recomputeItems()
    }

    /** A time-limited public URL for [file], or null on failure (error surfaced to state). */
    suspend fun shareLink(file: S3Item.File, duration: Duration): String? {
        val repository = repo ?: return null
        return runCatching { repository.presignedUrl(file.key, duration) }.getOrElse { e ->
            _state.update { it.copy(error = e.userMessage()) }
            null
        }
    }

    /** Applies the current search filter and sort to a folder listing (folders sort first). */
    private fun viewOf(
        items: List<S3Item>,
        query: String,
        mode: SortMode,
        ascending: Boolean,
    ): List<S3Item> {
        val q = query.trim()
        val filtered = if (q.isEmpty()) items else items.filter { it.name.contains(q, ignoreCase = true) }
        val folders = filtered.filterIsInstance<S3Item.Folder>().sortedBy { it.name.lowercase() }
        return folders + sortFiles(filtered.filterIsInstance<S3Item.File>(), mode, ascending)
    }

    /** Bucket-wide search: filter all files by name and sort (no folders). */
    private fun bucketView(
        files: List<S3Item.File>,
        query: String,
        mode: SortMode,
        ascending: Boolean,
    ): List<S3Item> {
        val q = query.trim()
        val filtered = if (q.isEmpty()) files else files.filter { it.name.contains(q, ignoreCase = true) }
        return sortFiles(filtered, mode, ascending)
    }

    private fun sortFiles(files: List<S3Item.File>, mode: SortMode, ascending: Boolean): List<S3Item.File> {
        val ordered = when (mode) {
            SortMode.NAME -> files.sortedBy { it.name.lowercase() }
            SortMode.DATE -> files.sortedBy { it.lastModifiedEpochSeconds }
            SortMode.SIZE -> files.sortedBy { it.size }
        }
        return if (ascending) ordered else ordered.reversed()
    }

    fun createFolder(name: String) {
        val repository = repo ?: return
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            runCatching { repository.createFolder(currentPrefix + clean) }
                .onSuccess {
                    _state.update { it.copy(isWorking = false) }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(isWorking = false, error = e.userMessage()) } }
        }
    }

    fun upload(uris: List<Uri>) {
        if (repo == null || uris.isEmpty()) return
        val prefix = currentPrefix
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            // Stage the picked content into cache files now (while we hold read access), then
            // hand the batch to WorkManager so the upload survives leaving the screen.
            val batch = mutableListOf<Transfer>()
            var failures = 0
            for (uri in uris) {
                runCatching {
                    val (name, staged) = stageUpload(uri)
                    batch += Transfer(prefix + name, staged.path, staged.length())
                }.onFailure { failures++ }
            }
            _state.update {
                it.copy(
                    isWorking = false,
                    error = if (failures == 0) null else "Couldn't read $failures of ${uris.size} file(s).",
                )
            }
            transfers.enqueueUploads(batch)
        }
    }

    fun cancelTransfers() = transfers.cancelAll()

    // --- Multi-select --------------------------------------------------------------------

    fun enterSelection(item: S3Item) =
        _state.update { it.copy(selecting = true, selected = setOf(item.key)) }

    fun toggleSelect(item: S3Item) = _state.update {
        val next = if (item.key in it.selected) it.selected - item.key else it.selected + item.key
        it.copy(selected = next, selecting = next.isNotEmpty())
    }

    fun selectAll() = _state.update {
        it.copy(selecting = true, selected = it.items.map { item -> item.key }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selecting = false, selected = emptySet()) }

    private fun selectedItems(): List<S3Item> {
        val keys = _state.value.selected
        return _state.value.items.filter { it.key in keys }
    }

    /**
     * Optimistically hides the selected items and emits an undo event. The screen shows an
     * "Undo" snackbar and then calls [commitDelete] (actually delete) or [undoDelete] (restore).
     */
    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        pendingDeletion = targets
        val keys = targets.map { it.key }.toSet()
        rawItems = rawItems.filterNot { it.key in keys }
        _state.update {
            it.copy(
                selecting = false,
                selected = emptySet(),
                items = viewOf(rawItems, it.query, it.sortMode, it.sortAscending),
            )
        }
        _deletedEvent.tryEmit(targets.size)
    }

    /** Restores items hidden by [deleteSelected] (nothing was removed from S3 yet). */
    fun undoDelete() {
        if (pendingDeletion.isEmpty()) return
        pendingDeletion = emptyList()
        refresh()
    }

    /** Commits the pending delete: actually removes the hidden items from S3 (folders recurse). */
    fun commitDelete() {
        val repository = repo ?: return
        val targets = pendingDeletion
        if (targets.isEmpty()) return
        pendingDeletion = emptyList()
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            var failures = 0
            for (target in targets) runCatching { repository.delete(target) }.onFailure { failures++ }
            _state.update {
                it.copy(
                    isWorking = false,
                    error = if (failures == 0) null else "Failed to delete $failures item(s).",
                )
            }
            refresh()
        }
    }

    /** Downloads the selected PDFs to the on-device cache without opening them. */
    fun downloadSelected() {
        val files = selectedItems().filterIsInstance<S3Item.File>().filter { it.isPdf }
        clearSelection()
        downloadForOffline(files)
    }

    /** Pre-caches [files] so they're available offline (and gain thumbnails). Skips ones already cached. */
    fun downloadForOffline(files: List<S3Item.File>) {
        if (repo == null) return
        session.pdfCacheDir.mkdirs()
        val batch = files.filter { it.isPdf }.mapNotNull { file ->
            val dest = session.cacheFileFor(file.key, file.eTag)
            if (dest.exists() && dest.length() > 0L) null else Transfer(file.key, dest.path, file.size)
        }
        transfers.enqueueDownloads(batch)
    }

    /** Renames [item] within its current folder. Implemented as an S3 copy + delete. */
    fun rename(item: S3Item, newName: String) {
        val repository = repo ?: return
        val clean = newName.trim().trim('/')
        if (clean.isEmpty() || clean == item.name) return
        val newKey = destKeyFor(item, currentPrefix, clean)
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, selecting = false, selected = emptySet()) }
            runCatching { repository.move(item, newKey) }
                .onSuccess {
                    _state.update { it.copy(isWorking = false) }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(isWorking = false, error = e.userMessage()) } }
        }
    }

    /** Enters "move" mode with [items]: the user then navigates to a destination and confirms. */
    fun startMove(items: List<S3Item>) =
        _state.update { it.copy(movingItems = items, selecting = false, selected = emptySet()) }

    fun cancelMove() = _state.update { it.copy(movingItems = emptyList()) }

    /** Moves the in-flight items into the folder currently being viewed, skipping no-op targets. */
    fun moveHere() {
        val repository = repo ?: return
        val items = _state.value.movingItems
        if (items.isEmpty()) return
        val destPrefix = currentPrefix
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, movingItems = emptyList()) }
            var failures = 0
            for (item in items) {
                val newKey = destKeyFor(item, destPrefix)
                // Skip dropping onto itself or a folder into its own subtree.
                if (newKey == item.key) continue
                if (item is S3Item.Folder && destPrefix.startsWith(item.key)) continue
                runCatching { repository.move(item, newKey) }.onFailure { failures++ }
            }
            _state.update {
                it.copy(
                    isWorking = false,
                    error = if (failures == 0) null else "Failed to move $failures item(s).",
                )
            }
            refresh()
        }
    }

    private fun destKeyFor(item: S3Item, destPrefix: String, name: String = item.name): String =
        if (item is S3Item.Folder) "$destPrefix$name/" else "$destPrefix$name"

    fun signOut() = session.reset()

    fun clearError() = _state.update { it.copy(error = null) }

    /** The on-device cached copy of [file] (for a list thumbnail), or null if not downloaded. */
    fun localPdf(file: S3Item.File): File? = session.cachedPdf(file.key, file.eTag)

    /** Copies a picked content Uri into a temp cache file and reports its display name. */
    private suspend fun stageUpload(uri: Uri): Pair<String, File> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val name = displayName(uri) ?: "upload_${System.currentTimeMillis()}.pdf"
        val staged = File.createTempFile("upload_", ".pdf", appContext.cacheDir)
        val input = resolver.openInputStream(uri) ?: error("Unable to open the selected file.")
        input.use { stream -> staged.outputStream().use { stream.copyTo(it) } }
        name to staged
    }

    private fun displayName(uri: Uri): String? {
        val resolver = appContext.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)?.ensurePdfExtension()
            }
        }
        return null
    }

    private fun String.ensurePdfExtension(): String =
        if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"
}

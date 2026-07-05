package com.pdfvault.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfvault.S3SessionManager
import com.pdfvault.data.ReaderPreferences
import com.pdfvault.data.ReadingDirection
import com.pdfvault.data.ReadingMode
import com.pdfvault.data.RecentsStore
import com.pdfvault.data.s3.S3Repository
import com.pdfvault.sync.SyncManager
import com.pdfvault.pdf.HighlightRect
import com.pdfvault.pdf.PageMatch
import com.pdfvault.pdf.PdfDocument
import com.pdfvault.pdf.PdfLink
import com.pdfvault.pdf.TocEntry
import com.pdfvault.pdf.extractPageText
import com.pdfvault.pdf.loadOutline
import com.pdfvault.pdf.loadPageLinks
import com.pdfvault.pdf.searchPdfHighlights
import com.pdfvault.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

sealed interface PdfViewerState {
    data object Loading : PdfViewerState
    data class Error(val message: String) : PdfViewerState
    data class Ready(
        val document: PdfDocument,
        val title: String,
        val outline: List<TocEntry> = emptyList(),
        val outlineLoading: Boolean = true,
        /** Tappable link regions per page index (URLs and in-document jumps). */
        val links: Map<Int, List<PdfLink>> = emptyMap(),
    ) : PdfViewerState
}

/** In-document text-search state for the reader's search bar. */
data class ReaderSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<PageMatch> = emptyList(),
    val currentIndex: Int = -1,
    /** Match rectangles to draw, per page index. Empty when not searching. */
    val highlights: Map<Int, List<HighlightRect>> = emptyMap(),
) {
    val current: PageMatch? get() = results.getOrNull(currentIndex)
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val session: S3SessionManager,
    private val readerPrefs: ReaderPreferences,
    private val recents: RecentsStore,
    private val sync: SyncManager,
) : ViewModel() {

    private var document: PdfDocument? = null
    private var openedKey: String? = null

    /** The exact cache file currently open, used for Share / Open-with / Save-a-copy. */
    @Volatile
    private var openedFile: File? = null

    private val _state = MutableStateFlow<PdfViewerState>(PdfViewerState.Loading)
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    private val _direction = MutableStateFlow(readerPrefs.readingDirection)
    val direction: StateFlow<ReadingDirection> = _direction.asStateFlow()

    private val _continuous = MutableStateFlow(readerPrefs.verticalContinuous)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()

    fun toggleContinuous() {
        val next = !_continuous.value
        readerPrefs.verticalContinuous = next
        _continuous.value = next
    }

    private val _search = MutableStateFlow(ReaderSearchState())
    val search: StateFlow<ReaderSearchState> = _search.asStateFlow()

    // One-shot page targets emitted by search navigation; the screen scrolls the pager to them.
    private val _jumpToPage = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val jumpToPage: SharedFlow<Int> = _jumpToPage.asSharedFlow()

    private var searchJob: Job? = null

    private val _readingMode = MutableStateFlow(readerPrefs.readingMode)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _brightness = MutableStateFlow(readerPrefs.readingBrightness)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _bookmarks = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarks: StateFlow<Set<Int>> = _bookmarks.asStateFlow()

    fun setReadingMode(mode: ReadingMode) {
        openedKey?.let { readerPrefs.setReadingMode(it, mode) } ?: run { readerPrefs.readingMode = mode }
        _readingMode.value = mode
    }

    /** Extracts the plain text of [page] for the copy-text sheet. */
    suspend fun pageText(page: Int): String {
        val file = openedFile ?: return ""
        return extractPageText(file, page)
    }

    fun setBrightness(value: Float) {
        readerPrefs.readingBrightness = value
        _brightness.value = value
    }

    /** Public page-jump used by the jump-to-page dialog and bookmark navigation. */
    fun jumpTo(page: Int) {
        _jumpToPage.tryEmit(page)
    }

    fun toggleBookmark(objectKey: String, page: Int) {
        val next = _bookmarks.value.toMutableSet().apply { if (!add(page)) remove(page) }
        readerPrefs.setBookmarks(objectKey, next)
        _bookmarks.value = next
    }

    fun toggleDirection() {
        val next = when (_direction.value) {
            ReadingDirection.VERTICAL -> ReadingDirection.HORIZONTAL
            ReadingDirection.HORIZONTAL -> ReadingDirection.VERTICAL
        }
        readerPrefs.readingDirection = next
        _direction.value = next
    }

    /** Last page the user was on for [objectKey], for resuming where they left off. */
    fun lastPage(objectKey: String): Int = readerPrefs.lastPage(objectKey)

    fun saveLastPage(objectKey: String, page: Int) = readerPrefs.setLastPage(objectKey, page)

    // --- In-document text search ----------------------------------------------------------

    fun updateSearchQuery(query: String) = _search.update { it.copy(query = query) }

    /** Runs a full-text search over the open PDF and jumps to the first match. */
    fun runSearch() {
        val query = _search.value.query.trim()
        val file = openedFile
        if (query.isEmpty() || file == null) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _search.update { it.copy(isSearching = true, hasSearched = true) }
            val results = searchPdfHighlights(file, query)
            val highlights = results.associate { it.pageIndex to it.rects }
            _search.update {
                it.copy(
                    isSearching = false,
                    results = results,
                    currentIndex = if (results.isEmpty()) -1 else 0,
                    highlights = highlights,
                )
            }
            results.firstOrNull()?.let { _jumpToPage.tryEmit(it.pageIndex) }
        }
    }

    fun goToResult(index: Int) {
        val results = _search.value.results
        if (index !in results.indices) return
        _search.update { it.copy(currentIndex = index) }
        _jumpToPage.tryEmit(results[index].pageIndex)
    }

    fun nextResult() {
        val s = _search.value
        if (s.results.isEmpty()) return
        goToResult((s.currentIndex + 1).mod(s.results.size))
    }

    fun prevResult() {
        val s = _search.value
        if (s.results.isEmpty()) return
        goToResult((s.currentIndex - 1).mod(s.results.size))
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = ReaderSearchState()
    }

    /**
     * Copies the cached PDF into a share dir under its real name (the cache file is named
     * by hash) and returns it, so Share / Open-with show a sensible filename.
     */
    suspend fun shareableFile(objectKey: String): File? = withContext(Dispatchers.IO) {
        val source = localFile(objectKey) ?: return@withContext null
        val name = objectKey.substringAfterLast('/').ifBlank { "document.pdf" }
        val dir = File(session.cacheDir, "share").apply { mkdirs() }
        val dest = File(dir, name)
        source.copyTo(dest, overwrite = true)
        dest
    }

    /** The locally-cached PDF file currently open, if it has been fully downloaded. */
    fun localFile(objectKey: String): File? =
        openedFile?.takeIf { it.exists() && it.length() > 0L }

    /** Downloads (if needed) and opens the PDF at [objectKey]. Safe to call repeatedly. */
    fun open(objectKey: String) {
        if (openedKey == objectKey && _state.value is PdfViewerState.Ready) return
        openedKey = objectKey

        val repository = session.repository
        if (repository == null) {
            _state.value = PdfViewerState.Error("Not connected to S3.")
            return
        }

        viewModelScope.launch {
            _state.value = PdfViewerState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    // Fetch the current ETag; null means we're offline (fall back to any cached copy).
                    val eTag = repository.objectETag(objectKey)
                    val target = session.cacheFileFor(objectKey, eTag)
                    val file = when {
                        // A complete copy for this exact version already on disk.
                        target.exists() && target.length() > 0L -> target
                        // Online: (re)download the current version.
                        eTag != null -> { downloadToCache(repository, objectKey, target); target }
                        // Offline: use the newest cached version we have for this key, if any.
                        else -> session.newestCachedFor(objectKey)
                            ?: error("You're offline and this PDF hasn't been downloaded yet.")
                    }
                    // Touch for LRU, then evict old/other cached PDFs over the cap (keeping this one).
                    file.setLastModified(System.currentTimeMillis())
                    session.prunePdfCache(MAX_CACHE_BYTES, keep = file)
                    openedFile = file
                    PdfDocument(file) to file
                }
            }.onSuccess { (doc, file) ->
                document?.close()
                document = doc
                recents.record(objectKey, doc.pageCount)
                sync.pushRecent(objectKey)
                _bookmarks.value = readerPrefs.bookmarks(objectKey)
                _readingMode.value = readerPrefs.readingMode(objectKey)
                _state.value = PdfViewerState.Ready(doc, objectKey.substringAfterLast('/'))
                loadOutlineFor(file, doc)
                loadLinksFor(file, doc)
            }.onFailure { e ->
                _state.value = PdfViewerState.Error(e.userMessage())
            }
        }
    }

    /** Parses the table-of-contents outline off the main path and folds it into the state. */
    private fun loadOutlineFor(file: File, doc: PdfDocument) {
        viewModelScope.launch {
            val outline = loadOutline(file)
            val current = _state.value
            if (current is PdfViewerState.Ready && current.document === doc) {
                _state.value = current.copy(outline = outline, outlineLoading = false)
            }
        }
    }

    /** Extracts tappable link regions off the main path and folds them into the state. */
    private fun loadLinksFor(file: File, doc: PdfDocument) {
        viewModelScope.launch {
            val links = loadPageLinks(file)
            val current = _state.value
            if (current is PdfViewerState.Ready && current.document === doc) {
                _state.value = current.copy(links = links)
            }
        }
    }

    /**
     * Downloads [objectKey] to a temp file and only promotes it to [cached] on full
     * success, so an interrupted download is never left behind to be reused.
     */
    private suspend fun downloadToCache(repository: S3Repository, objectKey: String, cached: File) {
        session.pdfCacheDir.mkdirs()
        val tmp = File.createTempFile("dl_", ".pdf", session.pdfCacheDir)
        try {
            repository.download(objectKey, tmp)
            if (!tmp.renameTo(cached)) {
                tmp.copyTo(cached, overwrite = true)
                tmp.delete()
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    override fun onCleared() {
        // Push the final reading position so it syncs to other devices.
        openedKey?.let { sync.pushRecent(it) }
        document?.close()
        document = null
    }

    private companion object {
        /** Soft cap on the on-device PDF cache; oldest files are evicted past this. */
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
    }
}

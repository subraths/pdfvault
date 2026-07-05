package com.pdfvault.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed as keyIsCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.data.AppStorage
import com.pdfvault.desktop.data.ReaderPreferences
import com.pdfvault.desktop.data.ReadingMode
import com.pdfvault.desktop.data.RecentsStore
import com.pdfvault.desktop.pdf.HighlightRect
import com.pdfvault.desktop.pdf.LinkTarget
import com.pdfvault.desktop.pdf.PageMatch
import com.pdfvault.desktop.pdf.PageRenderCache
import com.pdfvault.desktop.pdf.PdfDocument
import com.pdfvault.desktop.pdf.PdfLink
import com.pdfvault.desktop.pdf.TocEntry
import com.pdfvault.desktop.pdf.extractPageText
import com.pdfvault.desktop.pdf.loadOutline
import com.pdfvault.desktop.pdf.loadPageLinks
import com.pdfvault.desktop.pdf.printPdf
import com.pdfvault.desktop.pdf.searchPdfHighlights
import com.pdfvault.desktop.s3.S3Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val MAX_RENDER_WIDTH_PX = 3000
private val HIGHLIGHT_COLOR = Color(0xFFFFD54F).copy(alpha = 0.38f)

private enum class SidePanel { NONE, CONTENTS, BOOKMARKS, SEARCH, THUMBNAILS }

private data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<PageMatch> = emptyList(),
    val currentIndex: Int = -1,
    val highlights: Map<Int, List<HighlightRect>> = emptyMap(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ReaderScreen(
    repository: S3Repository?,
    target: OpenTarget,
    controller: ReaderController,
    onBack: () -> Unit,
) {
    val docId = target.docId
    var document by remember { mutableStateOf<PdfDocument?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf(SidePanel.NONE) }
    var readingMode by remember { mutableStateOf(ReaderPreferences.readingMode(docId)) }
    var bookmarks by remember { mutableStateOf(ReaderPreferences.bookmarks(docId)) }
    var outline by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var links by remember { mutableStateOf<Map<Int, List<PdfLink>>>(emptyMap()) }
    var search by remember { mutableStateOf(SearchState()) }
    var showJump by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var copyText by remember { mutableStateOf<String?>(null) }
    var showCopy by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val density = LocalDensity.current

    // Resolve the source (local file, or an S3 download to cache), then open + load outline/links.
    LaunchedEffect(target) {
        loadError = null
        runCatching {
            val file = when (target) {
                is OpenTarget.Local -> target.file
                is OpenTarget.Remote -> {
                    val cache = AppStorage.cacheFileFor(target.objectKey)
                    if (!cache.exists() || cache.length() == 0L) {
                        val repo = repository ?: error("Not connected to S3.")
                        withContext(Dispatchers.IO) { repo.download(target.objectKey, cache) }
                    }
                    cache
                }
            }
            withContext(Dispatchers.IO) { PdfDocument(file) }
        }.onSuccess { doc ->
            document = doc
            RecentsStore.record(docId, doc.pageCount)
            com.pdfvault.desktop.sync.SyncManager.pushRecent(docId)
            scope.launch { outline = loadOutline(doc.file) }
            scope.launch { links = loadPageLinks(doc.file) }
        }.onFailure { loadError = it.message ?: "Couldn't open the PDF." }
    }

    // Push the final reading position when leaving, so it syncs to other devices.
    DisposableEffect(Unit) {
        onDispose {
            com.pdfvault.desktop.sync.SyncManager.pushRecent(docId)
            document?.close()
        }
    }

    val pageCount = document?.pageCount ?: 0
    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    // Resume where the user left off, then persist the current page as they scroll.
    LaunchedEffect(document, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        val resumePage = ReaderPreferences.lastPage(docId).coerceIn(0, pageCount - 1)
        if (resumePage > 0) listState.scrollToItem(resumePage)
        snapshotFlow { listState.firstVisibleItemIndex }.collect { ReaderPreferences.setLastPage(docId, it) }
    }

    LaunchedEffect(panel) {
        if (panel == SidePanel.SEARCH) runCatching { searchFocus.requestFocus() }
    }

    fun goToPage(index: Int) {
        if (index in 0 until pageCount) scope.launch { listState.animateScrollToItem(index) }
    }

    fun runSearch() {
        val q = search.query.trim()
        val doc = document ?: return
        if (q.isEmpty()) return
        scope.launch {
            search = search.copy(searching = true, hasSearched = true)
            val results = searchPdfHighlights(doc.file, q)
            search = search.copy(
                searching = false,
                results = results,
                currentIndex = if (results.isEmpty()) -1 else 0,
                highlights = results.associate { it.pageIndex to it.rects },
            )
            results.firstOrNull()?.let { goToPage(it.pageIndex) }
        }
    }

    fun goToResult(index: Int) {
        val results = search.results
        if (index !in results.indices) return
        search = search.copy(currentIndex = index)
        goToPage(results[index].pageIndex)
    }

    fun toggleBookmark(page: Int) {
        val next = bookmarks.toMutableSet().apply { if (!add(page)) remove(page) }
        ReaderPreferences.setBookmarks(docId, next)
        bookmarks = next
    }

    fun setMode(mode: ReadingMode) {
        ReaderPreferences.setReadingMode(docId, mode)
        readingMode = mode
    }

    val colorFilter = remember(readingMode) { readingColorFilter(readingMode) }

    // Expose reader actions to the window menu bar. Callbacks close over live state (plain vars,
    // refreshed each recomposition so they never go stale — and never trigger one).
    SideEffect {
        controller.onZoomIn = { zoom = (zoom + 0.25f).coerceAtMost(MAX_ZOOM) }
        controller.onZoomOut = { zoom = (zoom - 0.25f).coerceAtLeast(MIN_ZOOM) }
        controller.onZoomReset = { zoom = 1f }
        controller.onNextPage = { goToPage(currentPage + 1) }
        controller.onPrevPage = { goToPage(currentPage - 1) }
        controller.onFirstPage = { goToPage(0) }
        controller.onLastPage = { goToPage(pageCount - 1) }
        controller.onGoToPage = { showJump = true }
        controller.onFind = { panel = SidePanel.SEARCH }
        controller.onBookmark = { toggleBookmark(currentPage) }
        controller.onFitWidth = { zoom = 1f }
        controller.onRotate = { rotation = (rotation + 90) % 360 }
        controller.onToggleThumbnails = {
            panel = if (panel == SidePanel.THUMBNAILS) SidePanel.NONE else SidePanel.THUMBNAILS
        }
        controller.onPrint = {
            document?.file?.let { f -> scope.launch { withContext(Dispatchers.IO) { printPdf(f) } } }
        }
        controller.onOpenExternal = { document?.file?.let { openInSystemViewer(it) } }
        controller.onReveal = { document?.file?.parentFile?.let { revealInFileManager(it) } }
    }
    DisposableEffect(Unit) {
        controller.hasDocument = true
        onDispose { controller.hasDocument = false }
    }

    // Focus the reader root once the document is up, so keyboard navigation works immediately.
    LaunchedEffect(document != null) {
        if (document != null) runCatching { rootFocus.requestFocus() }
    }

    val stepPx = remember(density) { with(density) { 180.dp.toPx() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            // All reader keys live here (the window menu bar is gone). onKeyEvent bubbles, so a
            // focused text field (search) handles keys first.
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val ctrl = ev.keyIsCtrlPressed
                when {
                    ctrl && ev.key == Key.Equals -> { zoom = (zoom + 0.25f).coerceAtMost(MAX_ZOOM); true }
                    ctrl && ev.key == Key.Minus -> { zoom = (zoom - 0.25f).coerceAtLeast(MIN_ZOOM); true }
                    ctrl && ev.key == Key.Zero -> { zoom = 1f; true }
                    ctrl && ev.key == Key.R -> { rotation = (rotation + 90) % 360; true }
                    ctrl && ev.key == Key.G -> { showJump = true; true }
                    ctrl && ev.key == Key.F -> { panel = SidePanel.SEARCH; true }
                    ctrl && ev.key == Key.B -> { toggleBookmark(currentPage); true }
                    ctrl && ev.key == Key.P -> { controller.onPrint(); true }
                    ctrl && ev.key == Key.MoveHome -> { goToPage(0); true }
                    ctrl && ev.key == Key.MoveEnd -> { goToPage(pageCount - 1); true }
                    ev.key == Key.F11 -> { controller.onToggleFullscreen(); true }
                    ev.key == Key.DirectionDown -> { scope.launch { listState.animateScrollBy(stepPx) }; true }
                    ev.key == Key.DirectionUp -> { scope.launch { listState.animateScrollBy(-stepPx) }; true }
                    ev.key == Key.Spacebar || ev.key == Key.PageDown -> { goToPage(currentPage + 1); true }
                    ev.key == Key.PageUp -> { goToPage(currentPage - 1); true }
                    ev.key == Key.MoveHome -> { goToPage(0); true }
                    ev.key == Key.MoveEnd -> { goToPage(pageCount - 1); true }
                    ev.key == Key.Escape -> {
                        if (panel != SidePanel.NONE) { panel = SidePanel.NONE; true } else { onBack(); true }
                    }
                    else -> false
                }
            }
            // Ctrl+wheel zooms (intercepted on the Initial pass so the list doesn't also scroll);
            // a plain wheel falls through to the list as usual.
            .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { e ->
                if (e.keyboardModifiers.isCtrlPressed) {
                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        zoom = (zoom - dy * 0.1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        e.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(target.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    if (pageCount > 0) {
                        Text(
                            "Page ${currentPage + 1} of $pageCount   •   ${(zoom * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showJump = true },
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (document != null) {
                    val bookmarked = currentPage in bookmarks
                    IconButton(onClick = { toggleBookmark(currentPage) }) {
                        Icon(
                            if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark page",
                        )
                    }
                    PanelToggle(Icons.Filled.Search, "Search", panel == SidePanel.SEARCH) {
                        panel = if (panel == SidePanel.SEARCH) SidePanel.NONE else SidePanel.SEARCH
                    }
                    PanelToggle(Icons.AutoMirrored.Filled.List, "Contents", panel == SidePanel.CONTENTS) {
                        panel = if (panel == SidePanel.CONTENTS) SidePanel.NONE else SidePanel.CONTENTS
                    }
                    PanelToggle(Icons.Filled.Bookmarks, "Bookmarks", panel == SidePanel.BOOKMARKS) {
                        panel = if (panel == SidePanel.BOOKMARKS) SidePanel.NONE else SidePanel.BOOKMARKS
                    }
                    Box {
                        IconButton(onClick = { showModeMenu = true }) {
                            Icon(Icons.Filled.Contrast, contentDescription = "Reading mode")
                        }
                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            ReadingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label()) },
                                    onClick = { setMode(mode); showModeMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        showCopy = true
                        copyText = null
                        val doc = document
                        if (doc != null) scope.launch { copyText = extractPageText(doc.file, currentPage) }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy page text")
                    }
                    IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(MIN_ZOOM) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                    }
                    IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(MAX_ZOOM) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                    }
                    // Actions that used to live on the window menu bar.
                    Box {
                        var showMore by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("Page thumbnails") },
                                onClick = {
                                    showMore = false
                                    panel = if (panel == SidePanel.THUMBNAILS) SidePanel.NONE else SidePanel.THUMBNAILS
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rotate right    Ctrl+R") },
                                onClick = { showMore = false; rotation = (rotation + 90) % 360 },
                            )
                            DropdownMenuItem(
                                text = { Text("Fullscreen    F11") },
                                onClick = { showMore = false; controller.onToggleFullscreen() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Print…    Ctrl+P") },
                                onClick = { showMore = false; controller.onPrint() },
                            )
                            DropdownMenuItem(
                                text = { Text("Open in default viewer") },
                                onClick = { showMore = false; controller.onOpenExternal() },
                            )
                            DropdownMenuItem(
                                text = { Text("Reveal in file manager") },
                                onClick = { showMore = false; controller.onReveal() },
                            )
                        }
                    }
                }
            },
        )

        Row(Modifier.fillMaxSize()) {
            if (panel != SidePanel.NONE && document != null) {
                Surface(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    tonalElevation = 2.dp,
                ) {
                    when (panel) {
                        SidePanel.CONTENTS -> ContentsPanel(outline) { goToPage(it) }
                        SidePanel.BOOKMARKS -> BookmarksPanel(bookmarks, onJump = { goToPage(it) }, onRemove = { toggleBookmark(it) })
                        SidePanel.THUMBNAILS -> ThumbnailsPanel(
                            document = document!!,
                            docId = docId,
                            current = currentPage,
                            onJump = { goToPage(it) },
                        )
                        SidePanel.SEARCH -> SearchPanel(
                            state = search,
                            focusRequester = searchFocus,
                            onQuery = { search = search.copy(query = it) },
                            onRun = { runSearch() },
                            onSelect = { goToResult(it) },
                        )
                        SidePanel.NONE -> {}
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    loadError != null -> Text(
                        loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )

                    document == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    else -> ContinuousReader(
                        document = document!!,
                        docId = docId,
                        zoom = zoom,
                        rotation = rotation,
                        colorFilter = colorFilter,
                        highlights = search.highlights,
                        links = links,
                        listState = listState,
                        onLink = { target ->
                            when (target) {
                                is LinkTarget.Web -> openInBrowser(target.uri)
                                is LinkTarget.Page -> goToPage(target.index)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showJump && pageCount > 0) {
        JumpDialog(pageCount, currentPage, onJump = { goToPage(it); showJump = false }, onDismiss = { showJump = false })
    }

    if (showCopy) {
        CopyTextDialog(
            pageNumber = currentPage + 1,
            text = copyText,
            onDismiss = { showCopy = false },
        )
    }
}

@Composable
private fun PanelToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Vertically-scrolling stack of all pages, with a desktop scrollbar and horizontal scroll when zoomed. */
@Composable
private fun ContinuousReader(
    document: PdfDocument,
    docId: String,
    zoom: Float,
    rotation: Int,
    colorFilter: ColorFilter?,
    highlights: Map<Int, List<HighlightRect>>,
    links: Map<Int, List<PdfLink>>,
    listState: LazyListState,
    onLink: (LinkTarget) -> Unit,
) {
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val pageCount = document.pageCount

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewportWidth = maxWidth
        val contentWidth = viewportWidth * zoom
        val renderWidthPx = remember(contentWidth, density) {
            with(density) { contentWidth.toPx() }.toInt().coerceIn(1, MAX_RENDER_WIDTH_PX)
        }

        // Prefetch a small forward-biased window so scrolling reveals ready pages, not spinners.
        LaunchedEffect(document, docId, renderWidthPx, rotation) {
            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { first ->
                prefetchPages(document, docId, first, renderWidthPx, rotation, pageCount)
            }
        }

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(contentWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pageCount, key = { it }, contentType = { "page" }) { index ->
                        ContinuousPage(
                            document = document,
                            docId = docId,
                            index = index,
                            width = contentWidth,
                            renderWidthPx = renderWidthPx,
                            rotation = rotation,
                            colorFilter = colorFilter,
                            highlights = highlights[index].orEmpty(),
                            links = links[index].orEmpty(),
                            onLink = onLink,
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ContinuousPage(
    document: PdfDocument,
    docId: String,
    index: Int,
    width: Dp,
    renderWidthPx: Int,
    rotation: Int,
    colorFilter: ColorFilter?,
    highlights: List<HighlightRect>,
    links: List<PdfLink>,
    onLink: (LinkTarget) -> Unit,
) {
    val aspect by produceState(0.707f, document, index, rotation) {
        value = runCatching { document.pageAspectRatio(index, rotation) }.getOrDefault(0.707f)
    }
    val bitmap by produceState<ImageBitmap?>(null, document, docId, index, renderWidthPx, rotation) {
        loadPageProgressive(document, docId, index, renderWidthPx, rotation) { value = it }
    }
    val pageHeight = if (aspect > 0f) width / aspect else width * 1.414f

    Box(
        modifier = Modifier.fillMaxWidth().height(pageHeight).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val rendered = bitmap
        if (rendered != null) {
            Image(
                bitmap = rendered,
                contentDescription = "Page ${index + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
            // Overlays live in unrotated page space, so only show them when the page isn't rotated.
            if (rotation == 0) {
                if (highlights.isNotEmpty()) HighlightOverlay(highlights)
                if (links.isNotEmpty()) LinkOverlay(links, onLink)
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun HighlightOverlay(rects: List<HighlightRect>) {
    Canvas(Modifier.fillMaxSize()) {
        for (r in rects) {
            drawRect(
                color = HIGHLIGHT_COLOR,
                topLeft = Offset(r.left * size.width, r.top * size.height),
                size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
            )
        }
    }
}

@Composable
private fun LinkOverlay(links: List<PdfLink>, onLink: (LinkTarget) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        for (link in links) {
            val r = link.rect
            Box(
                modifier = Modifier
                    .offset(x = w * r.left, y = h * r.top)
                    .size(width = w * (r.right - r.left), height = h * (r.bottom - r.top))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onLink(link.target) },
            )
        }
    }
}

// --- Side panels -----------------------------------------------------------------------------

@Composable
private fun ContentsPanel(entries: List<TocEntry>, onJump: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelTitle("Contents")
        if (entries.isEmpty()) {
            PanelHint("This PDF has no table of contents.")
        } else {
            val expanded = remember { mutableStateMapOf<Int, Boolean>() }
            val rows = ArrayList<TocRow>().also { flattenOutline(entries, 0, expanded, it) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.entry.id }) { row ->
                    TocItemRow(
                        row = row,
                        isExpanded = expanded[row.entry.id] == true,
                        onToggle = {
                            val e = row.entry
                            if (e.children.isNotEmpty()) expanded[e.id] = !(expanded[e.id] ?: false)
                            else e.pageIndex?.let(onJump)
                        },
                        onOpen = { row.entry.pageIndex?.let(onJump) },
                    )
                }
            }
        }
    }
}

private data class TocRow(val entry: TocEntry, val depth: Int)

private fun flattenOutline(entries: List<TocEntry>, depth: Int, expanded: Map<Int, Boolean>, out: MutableList<TocRow>) {
    for (entry in entries) {
        out += TocRow(entry, depth)
        if (entry.children.isNotEmpty() && expanded[entry.id] == true) {
            flattenOutline(entry.children, depth + 1, expanded, out)
        }
    }
}

@Composable
private fun TocItemRow(row: TocRow, isExpanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val entry = row.entry
    val hasChildren = entry.children.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasChildren) onToggle() else onOpen() }
            .padding(start = (12 + row.depth * 16).dp, end = 12.dp)
            .padding(vertical = 10.dp),
    ) {
        if (hasChildren) {
            Icon(
                if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.clickable { onToggle() },
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Text(
            entry.title,
            style = if (row.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable { entry.pageIndex?.let { onOpen() } },
        )
        entry.pageIndex?.let {
            Text("${it + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BookmarksPanel(bookmarks: Set<Int>, onJump: (Int) -> Unit, onRemove: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelTitle("Bookmarks")
        if (bookmarks.isEmpty()) {
            PanelHint("No bookmarks yet. Use the bookmark icon while reading to add one.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(bookmarks.sorted(), key = { it }) { page ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(page) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Page ${page + 1}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(page) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove bookmark")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPanel(
    state: SearchState,
    focusRequester: FocusRequester,
    onQuery: (String) -> Unit,
    onRun: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PanelTitle("Search")
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Search in document") },
            trailingIcon = {
                IconButton(onClick = onRun) { Icon(Icons.Filled.Search, contentDescription = "Search") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onRun() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(focusRequester),
        )
        Spacer(Modifier.height(8.dp))
        when {
            state.searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.results.isNotEmpty() -> {
                Text(
                    "${state.results.size} page(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.results.size) { i ->
                        val match = state.results[i]
                        Column(
                            Modifier.fillMaxWidth()
                                .background(if (i == state.currentIndex) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Page ${match.pageIndex + 1}" + if (match.count > 1) "  •  ${match.count} hits" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(match.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                }
            }

            state.hasSearched -> PanelHint("No matches.")
        }
    }
}

@Composable
private fun ThumbnailsPanel(document: PdfDocument, docId: String, current: Int, onJump: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelTitle("Pages")
        LazyColumn(Modifier.fillMaxSize()) {
            items(document.pageCount) { index ->
                ThumbCell(document, docId, index, selected = index == current, onClick = { onJump(index) })
            }
        }
    }
}

@Composable
private fun ThumbCell(document: PdfDocument, docId: String, index: Int, selected: Boolean, onClick: () -> Unit) {
    val thumbPx = 150
    val aspect by produceState(0.707f, document, index) {
        value = runCatching { document.pageAspectRatio(index) }.getOrDefault(0.707f)
    }
    val bitmap by produceState<ImageBitmap?>(null, document, index) {
        val key = PageRenderCache.key(docId, index, thumbPx)
        value = PageRenderCache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching { document.renderPageToWidth(index, thumbPx).toComposeImageBitmap() }
                .getOrNull()?.also { PageRenderCache.put(key, it) }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(120.dp / (if (aspect > 0f) aspect else 0.707f))
                .background(Color.White)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        }
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

// --- Dialogs ---------------------------------------------------------------------------------

@Composable
private fun JumpDialog(pageCount: Int, current: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf((current + 1).toString()) }
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(7) },
                singleLine = true,
                label = { Text("Page (1–$pageCount)") },
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onJump(page!! - 1) }) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CopyTextDialog(pageNumber: Int, text: String?, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page $pageNumber") },
        text = {
            when {
                text == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                text.isBlank() -> Text("No selectable text on this page.")
                else -> SelectionContainer(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !text.isNullOrBlank(),
                onClick = { text?.let { clipboard.setText(AnnotatedString(it)) }; onDismiss() },
            ) { Text("Copy all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// --- Helpers ---------------------------------------------------------------------------------

private fun ReadingMode.label(): String = when (this) {
    ReadingMode.NORMAL -> "Normal"
    ReadingMode.NIGHT -> "Night"
    ReadingMode.SEPIA -> "Sepia"
}

private fun readingColorFilter(mode: ReadingMode): ColorFilter? = when (mode) {
    ReadingMode.NORMAL -> null
    ReadingMode.NIGHT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    ReadingMode.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** Two-pass page load: a fast half-res preview (if not cached), then the crisp render. */
private suspend fun loadPageProgressive(
    document: PdfDocument,
    docId: String,
    index: Int,
    widthPx: Int,
    rotation: Int,
    emit: (ImageBitmap) -> Unit,
) = withContext(Dispatchers.IO) {
    val key = PageRenderCache.key(docId, index, widthPx, rotation)
    PageRenderCache.get(key)?.let { emit(it); return@withContext }

    val previewPx = (widthPx / 2).coerceAtLeast(1)
    if (previewPx < widthPx) {
        val previewKey = PageRenderCache.key(docId, index, previewPx, rotation)
        val preview = PageRenderCache.get(previewKey)
            ?: runCatching { document.renderPageToWidth(index, previewPx, rotation).toComposeImageBitmap() }.getOrNull()
                ?.also { PageRenderCache.put(previewKey, it) }
        preview?.let { emit(it) }
    }

    runCatching { document.renderPageToWidth(index, widthPx, rotation).toComposeImageBitmap() }.getOrNull()?.let {
        PageRenderCache.put(key, it)
        emit(it)
    }
}

private suspend fun prefetchPages(
    document: PdfDocument,
    docId: String,
    center: Int,
    widthPx: Int,
    rotation: Int,
    pageCount: Int,
) = withContext(Dispatchers.IO) {
    for (page in intArrayOf(center + 1, center + 2, center + 3, center - 1)) {
        if (page < 0 || page >= pageCount) continue
        val key = PageRenderCache.key(docId, page, widthPx, rotation)
        if (PageRenderCache.get(key) != null) continue
        runCatching { document.renderPageToWidth(page, widthPx, rotation).toComposeImageBitmap() }
            .getOrNull()?.let { PageRenderCache.put(key, it) }
    }
}

private fun openInBrowser(uri: String) {
    runCatching {
        val normalized = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(uri)) uri else "https://$uri"
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(normalized))
        }
    }
}

/** Opens the PDF in the OS's default viewer. */
private fun openInSystemViewer(file: File) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
}

/** Opens the containing folder in the system file manager. */
private fun revealInFileManager(dir: File) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir) }
}

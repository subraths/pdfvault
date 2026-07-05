package com.pdfvault.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfvault.data.ReadingDirection
import com.pdfvault.R
import com.pdfvault.data.ReadingMode
import com.pdfvault.pdf.HighlightRect
import com.pdfvault.pdf.PageRenderCache
import com.pdfvault.pdf.PdfDocument
import com.pdfvault.pdf.TocEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import android.net.Uri
import com.pdfvault.pdf.LinkTarget
import com.pdfvault.pdf.PdfLink
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.math.roundToInt

private const val MAX_RENDER_WIDTH_PX = 2048
// Cap for the higher-resolution re-render used when a continuous-mode zoom settles in.
private const val HI_RES_MAX_WIDTH_PX = 3072
private const val MAX_ZOOM = 5f
private val PAGE_BACKGROUND = Color(0xFF202124)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    objectKey: String,
    onBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(objectKey) { viewModel.open(objectKey) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var chromeVisible by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showReadingMode by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showCopyText by remember { mutableStateOf(false) }
    var copyText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val tocSheetState = rememberModalBottomSheetState()
    val resultsSheetState = rememberModalBottomSheetState()
    val readingSheetState = rememberModalBottomSheetState()
    val bookmarksSheetState = rememberModalBottomSheetState()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val searchFocus = remember { FocusRequester() }

    // While searching, device-back closes the search bar instead of leaving the reader.
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        viewModel.clearSearch()
    }

    // System "create document" picker for saving a copy of the PDF anywhere the user chooses.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) scope.launch {
            val src = viewModel.localFile(objectKey) ?: return@launch
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    ImmersiveSystemBars(visible = chromeVisible)
    KeepScreenOn()
    ReaderBrightness(brightness)

    val pageColorFilter = remember(readingMode) { readingColorFilter(readingMode) }
    val continuous by viewModel.continuous.collectAsStateWithLifecycle()
    val ready = state as? PdfViewerState.Ready
    val title = ready?.title ?: objectKey.substringAfterLast('/')
    val pageCount = ready?.document?.pageCount ?: 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val listState = rememberLazyListState()

    // Continuous ("infinite scroll") only applies to vertical reading; horizontal stays paged.
    val continuousMode = ready != null && direction == ReadingDirection.VERTICAL && continuous
    val currentPage = if (continuousMode) listState.firstVisibleItemIndex else pagerState.currentPage

    // Once the document is loaded (and on mode change): resume the last page, then persist changes.
    LaunchedEffect(objectKey, pageCount, continuousMode) {
        if (pageCount <= 0) return@LaunchedEffect
        val target = viewModel.lastPage(objectKey).coerceIn(0, pageCount - 1)
        if (continuousMode) {
            if (target > 0 && listState.firstVisibleItemIndex != target) listState.scrollToItem(target)
            snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.saveLastPage(objectKey, it) }
        } else {
            if (target > 0 && pagerState.currentPage != target) pagerState.scrollToPage(target)
            snapshotFlow { pagerState.currentPage }.collect { viewModel.saveLastPage(objectKey, it) }
        }
    }

    // Search / TOC navigation asks the active scroller to jump to a page.
    LaunchedEffect(continuousMode, pageCount) {
        viewModel.jumpToPage.collect { page ->
            if (page in 0 until pageCount) {
                if (continuousMode) listState.animateScrollToItem(page)
                else pagerState.animateScrollToPage(page)
            }
        }
    }

    // Focus the search field as soon as the search bar opens.
    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { searchFocus.requestFocus() }
    }

    // Immersive reading: hide the toolbars once the user starts scrolling; a tap brings them back.
    LaunchedEffect(continuousMode, ready != null) {
        if (ready == null) return@LaunchedEffect
        snapshotFlow { if (continuousMode) listState.isScrollInProgress else pagerState.isScrollInProgress }
            .collect { moving -> if (moving && chromeVisible && !searchOpen) chromeVisible = false }
    }

    // Offer to jump back to the start when a document reopens partway through.
    LaunchedEffect(objectKey, ready != null, pageCount) {
        if (ready == null || pageCount <= 0) return@LaunchedEffect
        val resume = viewModel.lastPage(objectKey).coerceIn(0, pageCount - 1)
        if (resume > 0) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.reader_resumed, resume + 1),
                actionLabel = context.getString(R.string.reader_restart),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.jumpTo(0)
        }
    }

    Scaffold(
        containerColor = PAGE_BACKGROUND,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (chromeVisible && searchOpen && ready != null) {
                SearchTopBar(
                    state = searchState,
                    focusRequester = searchFocus,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = { viewModel.runSearch() },
                    onPrev = viewModel::prevResult,
                    onNext = viewModel::nextResult,
                    onShowResults = { showResults = true },
                    onClose = {
                        searchOpen = false
                        viewModel.clearSearch()
                    },
                )
            } else if (chromeVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ready != null && pageCount > 0) {
                                Text(
                                    stringResource(R.string.reader_page_of, currentPage + 1, pageCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable { showJump = true },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (ready != null) {
                            val isBookmarked = currentPage in bookmarks
                            IconButton(onClick = { viewModel.toggleBookmark(objectKey, currentPage) }) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark
                                    else Icons.Filled.BookmarkBorder,
                                    contentDescription = stringResource(if (isBookmarked) R.string.cd_bookmark_remove else R.string.cd_bookmark_add),
                                )
                            }
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { showToc = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.cd_contents))
                            }
                            IconButton(onClick = { viewModel.toggleDirection() }) {
                                Icon(
                                    imageVector = if (direction == ReadingDirection.VERTICAL) {
                                        Icons.Filled.SwapVert
                                    } else {
                                        Icons.Filled.SwapHoriz
                                    },
                                    contentDescription = stringResource(R.string.cd_scroll_direction),
                                )
                            }
                            IconButton(onClick = { chromeVisible = false }) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.cd_fullscreen))
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_reading_mode)) },
                                    leadingIcon = { Icon(Icons.Filled.Contrast, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showReadingMode = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_bookmarks)) },
                                    leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showBookmarks = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_continuous)) },
                                    leadingIcon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                                    trailingIcon = {
                                        if (continuous) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.cd_on))
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleContinuous()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_share)) },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            viewModel.shareableFile(objectKey)?.let { sharePdf(context, it) }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_open_with)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            viewModel.shareableFile(objectKey)?.let { openPdfWith(context, it) }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_copy_text)) },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        copyText = null
                                        showCopyText = true
                                        scope.launch {
                                            copyText = viewModel.pageText(currentPage)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_save_copy)) },
                                    leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        saveLauncher.launch(title)
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            when (val s = state) {
                is PdfViewerState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PdfViewerState.Error ->
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )

                is PdfViewerState.Ready ->
                    if (continuousMode) {
                        ContinuousVerticalPdf(
                            document = s.document,
                            docId = s.cacheId,
                            listState = listState,
                            colorFilter = pageColorFilter,
                            highlights = searchState.highlights,
                            links = s.links,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onLink = { target ->
                                when (target) {
                                    is LinkTarget.Web -> openUri(context, target.uri)
                                    is LinkTarget.Page -> viewModel.jumpTo(target.index)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PdfPager(
                            document = s.document,
                            docId = s.cacheId,
                            pagerState = pagerState,
                            direction = direction,
                            colorFilter = pageColorFilter,
                            highlights = searchState.highlights,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }

            // Thin overall reading-progress line along the bottom, shown with the chrome.
            if (chromeVisible && ready != null && pageCount > 0) {
                LinearProgressIndicator(
                    progress = { (currentPage + 1f) / pageCount },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }

    if (showJump && pageCount > 0) {
        JumpToPageDialog(
            pageCount = pageCount,
            current = currentPage,
            onJump = { page ->
                viewModel.jumpTo(page)
                showJump = false
            },
            onDismiss = { showJump = false },
        )
    }

    if (showReadingMode) {
        ModalBottomSheet(onDismissRequest = { showReadingMode = false }, sheetState = readingSheetState) {
            ReadingModeSheet(
                mode = readingMode,
                brightness = brightness,
                onMode = viewModel::setReadingMode,
                onBrightness = viewModel::setBrightness,
            )
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }, sheetState = bookmarksSheetState) {
            BookmarksSheet(
                bookmarks = bookmarks,
                onJump = { page ->
                    viewModel.jumpTo(page)
                    showBookmarks = false
                },
                onRemove = { page -> viewModel.toggleBookmark(objectKey, page) },
            )
        }
    }

    if (showCopyText) {
        ModalBottomSheet(onDismissRequest = { showCopyText = false }) {
            CopyTextSheet(
                pageNumber = currentPage + 1,
                text = copyText,
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    showCopyText = false
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.reader_copied)) }
                },
            )
        }
    }

    if (showToc && ready != null) {
        ModalBottomSheet(onDismissRequest = { showToc = false }, sheetState = tocSheetState) {
            TableOfContents(
                entries = ready.outline,
                loading = ready.outlineLoading,
                onJump = { index ->
                    scope.launch {
                        if (continuousMode) listState.scrollToItem(index)
                        else pagerState.scrollToPage(index)
                        showToc = false
                    }
                },
            )
        }
    }

    if (showResults) {
        ModalBottomSheet(onDismissRequest = { showResults = false }, sheetState = resultsSheetState) {
            SearchResultsList(
                state = searchState,
                onSelect = { index ->
                    viewModel.goToResult(index)
                    showResults = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    state: ReaderSearchState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowResults: () -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search))
            }
        },
        title = {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = {
            when {
                state.isSearching ->
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp))

                state.results.isNotEmpty() -> {
                    Text(
                        text = "${state.currentIndex + 1}/${state.results.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable(onClick = onShowResults)
                            .padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_previous_match))
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_next_match))
                    }
                }

                state.hasSearched ->
                    Text(
                        stringResource(R.string.reader_no_matches),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
private fun SearchResultsList(
    state: ReaderSearchState,
    onSelect: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.reader_results, state.results.size, state.query),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        items(state.results.size) { index ->
            val match = state.results[index]
            val selected = index == state.currentIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.reader_page_n, match.pageIndex + 1) +
                        if (match.count > 1) "  •  " + stringResource(R.string.reader_page_hits, match.count) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = match.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun PdfPager(
    document: PdfDocument,
    docId: String,
    pagerState: androidx.compose.foundation.pager.PagerState,
    direction: ReadingDirection,
    colorFilter: ColorFilter?,
    highlights: Map<Int, List<HighlightRect>>,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // beyondViewportPageCount = 1 composes the neighbouring page ahead of time so its
        // bitmap is already rendering before you swipe to it — the page is there instantly.
        if (direction == ReadingDirection.VERTICAL) {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePdfPage(document, docId, page, colorFilter, highlights[page].orEmpty(), onToggleChrome)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePdfPage(document, docId, page, colorFilter, highlights[page].orEmpty(), onToggleChrome)
            }
        }
    }
}

@Composable
private fun ZoomablePdfPage(
    document: PdfDocument,
    docId: String,
    index: Int,
    colorFilter: ColorFilter?,
    highlights: List<HighlightRect>,
    onToggleChrome: () -> Unit,
) {
    val zoomState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        // Oversample (2x) so text stays sharp when zoomed, capped to bound memory.
        val renderPx = remember(widthPx) { (widthPx * 2).coerceIn(1, MAX_RENDER_WIDTH_PX) }

        // Emits a fast, soft preview first (if not already cached) then the crisp render,
        // so a page never shows a blank spinner as it scrolls in.
        val bitmap by produceState<Bitmap?>(null, document, docId, index, renderPx, highlights) {
            loadPageProgressive(document, docId, index, renderPx, highlights) { value = it }
        }

        val rendered = bitmap
        if (rendered != null) {
            // Tell Telephoto the real content bounds so zoom limits & panning are correct.
            LaunchedEffect(zoomState, rendered) {
                zoomState.setContentLocation(
                    ZoomableContentLocation.scaledInsideAndCenterAligned(
                        Size(rendered.width.toFloat(), rendered.height.toFloat()),
                    ),
                )
            }
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(R.string.reader_page_n, index + 1),
                contentScale = ContentScale.Inside,
                alignment = Alignment.Center,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    // Double tap toggles the toolbars/fullscreen (in place of double-tap-to-zoom);
                    // single taps do nothing so nothing steals them.
                    .zoomable(
                        state = zoomState,
                        onDoubleClick = DoubleClickToZoomListener { _, _ -> onToggleChrome() },
                    ),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Continuous ("infinite scroll") vertical reader: all pages stacked in a freely-scrolling
 * LazyColumn instead of snapping page-by-page. Pinch (two fingers) zooms the whole column and
 * lets you pan horizontally; a single finger always scrolls, and a single tap toggles chrome.
 * When a zoom settles above a threshold, visible pages re-render at higher resolution so text
 * stays crisp instead of being GPU-upscaled.
 */
@OptIn(FlowPreview::class)
@Composable
private fun ContinuousVerticalPdf(
    document: PdfDocument,
    docId: String,
    listState: LazyListState,
    colorFilter: ColorFilter?,
    highlights: Map<Int, List<HighlightRect>>,
    links: Map<Int, List<PdfLink>>,
    onToggleChrome: () -> Unit,
    onLink: (LinkTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                // Double tap toggles the toolbars/fullscreen. Single taps are deliberately left
                // unhandled here so they fall through to link regions on the page.
                detectTapGestures(onDoubleTap = { onToggleChrome() })
            }
            .pointerInput(Unit) {
                // Handled on the Initial pass so single-finger scrolling still reaches the list.
                detectPinchPan { zoom, panX ->
                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    scale = newScale
                    offsetX = if (newScale <= 1f) {
                        0f
                    } else {
                        val maxOffset = size.width * (newScale - 1f) / 2f
                        (offsetX + panX).coerceIn(-maxOffset, maxOffset)
                    }
                }
            },
    ) {
        val widthDp = maxWidth
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        val renderWidthPx = remember(widthPx) { (widthPx * 2).coerceIn(1, MAX_RENDER_WIDTH_PX) }
        val pageCount = document.pageCount

        // When a pinch-zoom settles above ~1.75x, re-render pages at higher resolution so magnified
        // text is crisp instead of GPU-upscaled; otherwise stay at the light base width. Debounced
        // so an in-progress pinch doesn't thrash the renderer.
        var settledScale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(Unit) {
            snapshotFlow { scale }.debounce(160).collect { settledScale = it }
        }
        val effectiveRenderPx = remember(renderWidthPx, settledScale) {
            if (settledScale >= 1.75f) (renderWidthPx * 2).coerceAtMost(HI_RES_MAX_WIDTH_PX) else renderWidthPx
        }

        // Render a small forward-biased window of pages into the cache ahead of the viewport,
        // so scrolling reveals already-rendered pages instead of spinners. Debounced so prefetch
        // only runs when scrolling pauses — it shares one render mutex with the visible pages and
        // must never queue ahead of them. collectLatest cancels stale work between pages.
        LaunchedEffect(document, docId, renderWidthPx) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .debounce(150)
                .collectLatest { first ->
                    prefetchPages(document, docId, first, renderWidthPx, pageCount)
                }
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            items(pageCount, key = { it }, contentType = { "page" }) { index ->
                ContinuousPage(
                    document = document,
                    docId = docId,
                    index = index,
                    width = widthDp,
                    renderWidthPx = effectiveRenderPx,
                    colorFilter = colorFilter,
                    highlights = highlights[index].orEmpty(),
                    links = links[index].orEmpty(),
                    onLink = onLink,
                )
            }
        }

        // Draggable fast-scroll handle for long documents; auto-hides when idle.
        FastScrollThumb(
            listState = listState,
            pageCount = pageCount,
            onScrubToPage = { page -> scope.launch { listState.scrollToItem(page) } },
        )
    }
}

@Composable
private fun ContinuousPage(
    document: PdfDocument,
    docId: String,
    index: Int,
    width: Dp,
    renderWidthPx: Int,
    colorFilter: ColorFilter?,
    highlights: List<HighlightRect>,
    links: List<PdfLink>,
    onLink: (LinkTarget) -> Unit,
) {
    // Reserve the page's real height first (cheap) so scroll offsets stay stable while it renders.
    val aspect by produceState(0.707f, document, index) {
        value = runCatching { document.pageAspectRatio(index) }.getOrDefault(0.707f)
    }
    val bitmap by produceState<Bitmap?>(null, document, docId, index, renderWidthPx, highlights) {
        loadPageProgressive(document, docId, index, renderWidthPx, highlights) { value = it }
    }
    val pageHeight = if (aspect > 0f) width / aspect else width * 1.414f

    Box(
        modifier = Modifier.fillMaxWidth().height(pageHeight),
        contentAlignment = Alignment.Center,
    ) {
        val rendered = bitmap
        if (rendered != null) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(R.string.reader_page_n, index + 1),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
            // The page image exactly fills this box, so normalized link rects map 1:1 onto it.
            if (links.isNotEmpty()) LinkOverlay(links = links, onLink = onLink)
        } else {
            CircularProgressIndicator()
        }
    }
}

/**
 * Transparent tappable regions over a page for its PDF link annotations. Positioned by the links'
 * normalized rectangles; taps open URLs or jump within the document and consume the gesture, so
 * they don't also toggle the chrome.
 */
@Composable
private fun LinkOverlay(links: List<PdfLink>, onLink: (LinkTarget) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        for (link in links) {
            val r = link.rect
            Box(
                modifier = Modifier
                    .offset(x = w * r.left, y = h * r.top)
                    .size(width = w * (r.right - r.left), height = h * (r.bottom - r.top))
                    .clickable(onClickLabel = stringResource(R.string.cd_open_link)) { onLink(link.target) },
            )
        }
    }
}

// Translucent amber for baked-in search highlights.
private val HIGHLIGHT_COLOR = android.graphics.Color.argb(96, 255, 213, 79)

/**
 * Loads a page for display in two passes so it never shows as a blank spinner while scrolling:
 *
 *  1. If the crisp render is already cached, emit it and stop.
 *  2. Otherwise emit a fast half-resolution preview (slightly soft, near-instant) so something
 *     is on screen immediately, then emit the crisp full-resolution render.
 *
 * Clean (un-highlighted) bitmaps are cached in [PageRenderCache]; search [highlights] are painted
 * onto a copy so they zoom and pan with the page.
 */
private suspend fun loadPageProgressive(
    document: PdfDocument,
    docId: String,
    index: Int,
    widthPx: Int,
    highlights: List<HighlightRect>,
    emit: (Bitmap) -> Unit,
) = withContext(Dispatchers.IO) {
    fun present(clean: Bitmap) = if (highlights.isEmpty()) clean else clean.withHighlights(highlights)

    // 1) Crisp cache hit — nothing more to do.
    val key = PageRenderCache.key(docId, index, widthPx)
    PageRenderCache.get(key)?.let { emit(present(it)); return@withContext }

    // 2) Instant soft preview at half resolution while the crisp render is prepared.
    val previewPx = (widthPx / 2).coerceAtLeast(1)
    if (previewPx < widthPx) {
        val previewKey = PageRenderCache.key(docId, index, previewPx)
        val preview = PageRenderCache.get(previewKey)
            ?: runCatching { document.renderPage(index, previewPx) }.getOrNull()
                ?.also { PageRenderCache.put(previewKey, it) }
        preview?.let { emit(present(it)) }
    }

    // 3) Crisp full-resolution render.
    runCatching { document.renderPage(index, widthPx).also { PageRenderCache.put(key, it) } }
        .getOrNull()?.let { emit(present(it)) }
}

/**
 * Renders a small forward-biased window of pages around [center] into [PageRenderCache] so they
 * are ready before the user scrolls to them. Skips pages already cached; renders sequentially and
 * is cancellation-friendly, so a fast scroll abandons stale work.
 */
private suspend fun prefetchPages(
    document: PdfDocument,
    docId: String,
    center: Int,
    widthPx: Int,
    pageCount: Int,
) = withContext(Dispatchers.IO) {
    for (page in intArrayOf(center + 1, center + 2, center + 3, center - 1)) {
        if (page < 0 || page >= pageCount) continue
        val key = PageRenderCache.key(docId, page, widthPx)
        if (PageRenderCache.get(key) != null) continue
        runCatching { document.renderPage(page, widthPx).also { PageRenderCache.put(key, it) } }
    }
}

private fun Bitmap.withHighlights(rects: List<HighlightRect>): Bitmap {
    // Copy so the cached clean bitmap is never mutated.
    val out = copy(Bitmap.Config.ARGB_8888, true) ?: return this
    val canvas = Canvas(out)
    val paint = Paint().apply { color = HIGHLIGHT_COLOR; style = Paint.Style.FILL }
    for (r in rects) {
        canvas.drawRect(
            r.left * out.width,
            r.top * out.height,
            r.right * out.width,
            r.bottom * out.height,
            paint,
        )
    }
    return out
}

/**
 * Reports two-finger pinch/horizontal-pan gestures on the [PointerEventPass.Initial] pass and
 * consumes only those, so single-finger drags fall through to a scrolling parent/child.
 */
private suspend fun PointerInputScope.detectPinchPan(onGesture: (zoom: Float, panX: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                val zoom = event.calculateZoom()
                val panX = event.calculatePan().x
                if (zoom != 1f || panX != 0f) {
                    onGesture(zoom, panX)
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** A row of the flattened, currently-visible outline tree. */
private data class TocRow(val entry: TocEntry, val depth: Int)

private fun flattenOutline(
    entries: List<TocEntry>,
    depth: Int,
    expanded: Map<Int, Boolean>,
    out: MutableList<TocRow>,
) {
    for (entry in entries) {
        out += TocRow(entry, depth)
        if (entry.children.isNotEmpty() && expanded[entry.id] == true) {
            flattenOutline(entry.children, depth + 1, expanded, out)
        }
    }
}

@Composable
private fun TableOfContents(entries: List<TocEntry>, loading: Boolean, onJump: (Int) -> Unit) {
    Text(
        stringResource(R.string.reader_toc_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    when {
        loading && entries.isEmpty() ->
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        entries.isEmpty() ->
            Text(
                stringResource(R.string.reader_toc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )

        else -> {
            // Tracks which chapters/sections are expanded; collapsed by default.
            val expanded = remember { mutableStateMapOf<Int, Boolean>() }
            val rows = ArrayList<TocRow>().also { flattenOutline(entries, 0, expanded, it) }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(rows, key = { it.entry.id }) { row ->
                    TocItemRow(
                        row = row,
                        isExpanded = expanded[row.entry.id] == true,
                        onToggle = {
                            val entry = row.entry
                            if (entry.children.isNotEmpty()) {
                                expanded[entry.id] = !(expanded[entry.id] ?: false)
                            } else {
                                entry.pageIndex?.let(onJump)
                            }
                        },
                        onNavigate = { row.entry.pageIndex?.let(onJump) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocItemRow(
    row: TocRow,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigate: () -> Unit,
) {
    val entry = row.entry
    val hasChildren = entry.children.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Single tap toggles a chapter's sections (or opens a leaf); double tap on any
            // entry jumps straight to its page. The first tap fires immediately (no
            // double-tap disambiguation delay) so toggling stays snappy.
            .pointerInput(entry.id) {
                detectTapAndDoubleTap(onTap = { onToggle() }, onDoubleTap = { onNavigate() })
            }
            .padding(start = (12 + row.depth * 18).dp, end = 12.dp)
            .padding(vertical = 12.dp),
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Text(
            text = entry.title,
            style = if (row.depth == 0) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.pageIndex?.let { page ->
            Text(
                text = "${page + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A draggable fast-scroll handle pinned to the right edge of the continuous reader. It appears
 * while the list is moving (or being dragged) and fades out when idle; dragging it scrubs through
 * the document and shows a page-number bubble. Hidden for very short documents.
 */
@Composable
private fun BoxScope.FastScrollThumb(
    listState: LazyListState,
    pageCount: Int,
    onScrubToPage: (Int) -> Unit,
) {
    if (pageCount <= 3) return

    val density = LocalDensity.current
    val fastScrollLabel = stringResource(R.string.cd_fast_scroll)
    var dragging by remember { mutableStateOf(false) }
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    // Show the handle whenever the list is in motion or the user is holding it.
    val visible = listState.isScrollInProgress || dragging

    BoxWithConstraints(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(44.dp),
    ) {
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbHeight = 48.dp
        val thumbPx = with(density) { thumbHeight.toPx() }
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)

        // Where the thumb sits: driven by the drag while active, otherwise by scroll progress.
        var dragY by remember { mutableFloatStateOf(0f) }
        val progressY = if (pageCount > 1) firstVisible.toFloat() / (pageCount - 1) * travel else 0f
        val thumbY = if (dragging) dragY else progressY

        val dragState = rememberDraggableState { delta ->
            dragY = (dragY + delta).coerceIn(0f, travel)
            val fraction = dragY / travel
            onScrubToPage((fraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1))
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Page bubble, shown only while actively scrubbing.
                if (dragging) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(x = 0, y = (thumbY).roundToInt()) }
                            .offset(x = (-52).dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reader_page_of, firstVisible + 1, pageCount),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = 0, y = thumbY.roundToInt()) }
                        .padding(end = 4.dp)
                        .size(width = 8.dp, height = thumbHeight)
                        .semantics { contentDescription = fastScrollLabel }
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                dragY = progressY
                                dragging = true
                            },
                            onDragStopped = { dragging = false },
                        ),
                )
            }
        }
    }
}

/**
 * Like detectTapGestures but fires [onTap] on the first tap immediately instead of waiting
 * out the double-tap timeout, so a single tap feels instant. A quick second tap additionally
 * triggers [onDoubleTap].
 */
private suspend fun PointerInputScope.detectTapAndDoubleTap(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        waitForUpOrCancellation() ?: return@awaitEachGesture
        onTap()
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false)
        } ?: return@awaitEachGesture
        secondDown.consume()
        if (waitForUpOrCancellation() != null) onDoubleTap()
    }
}

/** Hides/shows the system status & navigation bars to support a fullscreen reading mode. */
@Composable
private fun ImmersiveSystemBars(visible: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(visible, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Keeps the screen awake while the reader is on-screen. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun contentUriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun sharePdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, contentUriFor(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.chooser_share_pdf)))
}

private fun openPdfWith(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUriFor(context, file), "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.chooser_open_with)))
}

// Matches a leading URI scheme like "https:", "mailto:", "tel:".
private val URI_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/** Opens a tapped in-document hyperlink in the user's browser (or matching app). */
private fun openUri(context: Context, uri: String) {
    runCatching {
        // Bare domains (no scheme) default to https; scheme links (mailto:, tel:, …) pass through.
        val normalized = if (URI_SCHEME.containsMatchIn(uri)) uri else "https://$uri"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }
}

/**
 * Bottom sheet showing the current page's extracted text in a selectable, scrollable block with a
 * one-tap "copy all". Shows a spinner while extraction runs and a hint when the page has no text.
 */
@Composable
private fun CopyTextSheet(pageNumber: Int, text: String?, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.reader_page_n, pageNumber),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (!text.isNullOrBlank()) {
                TextButton(onClick = { onCopy(text) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy_all))
                }
            }
        }

        when {
            text == null ->
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            text.isBlank() ->
                Text(
                    stringResource(R.string.reader_copy_text_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

            else ->
                SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
        }
    }
}

/** Color treatment for the rendered page: Night inverts, Sepia warms; Normal leaves it as-is. */
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

/** Applies the user's reading brightness to the window while the reader is on screen. */
@Composable
private fun ReaderBrightness(brightness: Float) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, brightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness =
                    if (brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    else brightness.coerceIn(0.05f, 1f)
            }
        }
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    current: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf((current + 1).toString()) }
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_jump_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.reader_jump_label, pageCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onJump(page!! - 1) }) { Text(stringResource(R.string.reader_jump_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ReadingModeSheet(
    mode: ReadingMode,
    brightness: Float,
    onMode: (ReadingMode) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.reader_reading_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == ReadingMode.NORMAL, { onMode(ReadingMode.NORMAL) }, label = { Text(stringResource(R.string.reader_mode_normal)) })
            FilterChip(mode == ReadingMode.NIGHT, { onMode(ReadingMode.NIGHT) }, label = { Text(stringResource(R.string.reader_mode_night)) })
            FilterChip(mode == ReadingMode.SEPIA, { onMode(ReadingMode.SEPIA) }, label = { Text(stringResource(R.string.reader_mode_sepia)) })
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.reader_brightness), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = if (brightness < 0f) 0.5f else brightness,
            onValueChange = onBrightness,
            valueRange = 0.05f..1f,
        )
        TextButton(onClick = { onBrightness(-1f) }) { Text(stringResource(R.string.reader_brightness_system)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BookmarksSheet(
    bookmarks: Set<Int>,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Text(
        stringResource(R.string.reader_bookmarks),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp),
    )
    if (bookmarks.isEmpty()) {
        Text(
            stringResource(R.string.reader_no_bookmarks),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(bookmarks.sorted(), key = { it }) { page ->
                ListItem(
                    modifier = Modifier.clickable { onJump(page) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.reader_page_n, page + 1)) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(page) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_bookmark_remove))
                        }
                    },
                )
            }
        }
    }
}

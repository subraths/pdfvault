package com.pdfvault.ui.browser

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfvault.R
import com.pdfvault.data.model.S3Item
import com.pdfvault.transfer.TransferKind
import com.pdfvault.transfer.TransferProgress
import com.pdfvault.ui.PdfThumbnail
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenPdf: (String) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<S3Item?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareLinkFor by remember { mutableStateOf<S3Item.File?>(null) }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val selectedItems = state.items.filter { it.key in state.selected }
    val bucketSearch = state.searchScope == SearchScope.BUCKET

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.upload(uris) }

    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }

    // Back priority (last registered wins): selection → search → up a folder.
    BackHandler(enabled = state.canGoUp) { viewModel.goUp() }
    BackHandler(enabled = searching) {
        searching = false
        viewModel.exitSearch()
    }
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }

    state.error?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Undoable delete: show a snackbar; commit the delete unless the user taps Undo.
    LaunchedEffect(Unit) {
        viewModel.deletedEvent.collect { count ->
            val message = if (count == 1) context.getString(R.string.browser_deleted_one)
            else context.getString(R.string.browser_deleted_many, count)
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.commitDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                state.selecting -> SelectionBar(
                    count = selectedItems.size,
                    canRename = selectedItems.size == 1,
                    canDownload = selectedItems.any { it is S3Item.File && it.isPdf },
                    canLink = selectedItems.size == 1 && selectedItems.first() is S3Item.File,
                    onClose = { viewModel.clearSelection() },
                    onRename = { pendingRename = selectedItems.firstOrNull() },
                    onMove = { viewModel.startMove(selectedItems) },
                    onDownload = { viewModel.downloadSelected() },
                    onShareLink = { shareLinkFor = selectedItems.firstOrNull() as? S3Item.File },
                    onDelete = { confirmDelete = true },
                    onSelectAll = { viewModel.selectAll() },
                )

                else -> TopAppBar(
                    title = {
                        if (searching) {
                            SearchField(
                                query = state.query,
                                onChange = viewModel::setQuery,
                                focus = searchFocus,
                                bucketScope = bucketSearch,
                            )
                        } else {
                            Breadcrumb(
                                bucket = state.bucket,
                                path = state.path,
                                onNavigate = viewModel::navigateTo,
                            )
                        }
                    },
                    navigationIcon = {
                        when {
                            searching -> IconButton(onClick = {
                                searching = false
                                viewModel.exitSearch()
                            }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search)) }

                            state.canGoUp -> IconButton(onClick = { viewModel.goUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_up))
                            }
                        }
                    },
                    actions = {
                        if (searching) {
                            IconButton(onClick = { viewModel.toggleSearchScope() }) {
                                Icon(
                                    imageVector = if (bucketSearch) Icons.Filled.Public else Icons.Filled.FolderOpen,
                                    contentDescription = stringResource(if (bucketSearch) R.string.cd_search_bucket else R.string.cd_search_this_folder),
                                    tint = if (bucketSearch) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current,
                                )
                            }
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                                }
                            }
                        } else {
                            IconButton(onClick = { searching = true }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { grid = !grid }) {
                                Icon(
                                    imageVector = if (grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = stringResource(if (grid) R.string.cd_list_view else R.string.cd_grid_view),
                                )
                            }
                            IconButton(onClick = { showSort = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
                            }
                            DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                SortMenuItem(state, SortMode.NAME, stringResource(R.string.sort_name)) { viewModel.setSort(it); showSort = false }
                                SortMenuItem(state, SortMode.DATE, stringResource(R.string.sort_date)) { viewModel.setSort(it); showSort = false }
                                SortMenuItem(state, SortMode.SIZE, stringResource(R.string.sort_size)) { viewModel.setSort(it); showSort = false }
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_new_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showNewFolder = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_upload_pdf)) },
                                    leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                                    enabled = !state.isWorking,
                                    onClick = {
                                        showMenu = false
                                        filePicker.launch("application/pdf")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_refresh)) },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.refresh()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                state.transfer?.let { transfer ->
                    TransferBar(progress = transfer, onCancel = { viewModel.cancelTransfers() })
                }
                val moving = state.movingItems
                if (moving.isNotEmpty()) {
                    val dest = state.path
                    val canMoveHere = moving.any { item ->
                        destKeyFor(item, dest) != item.key &&
                            !(item is S3Item.Folder && dest.startsWith(item.key))
                    }
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (moving.size == 1) stringResource(R.string.browser_moving_one, moving.first().name)
                                else stringResource(R.string.browser_moving_many, moving.size),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { viewModel.cancelMove() }) { Text(stringResource(R.string.action_cancel)) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.moveHere() },
                                enabled = canMoveHere && !state.isWorking,
                            ) { Text(stringResource(R.string.browser_move_here)) }
                        }
                    }
                }
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.items.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            val onItemClick: (S3Item) -> Unit = { item ->
                when {
                    state.selecting -> viewModel.toggleSelect(item)
                    item is S3Item.Folder -> viewModel.openFolder(item)
                    item is S3Item.File ->
                        if (item.isPdf) onOpenPdf(item.key) else viewModel.clearError()
                }
            }
            val onItemLongClick: (S3Item) -> Unit = { item ->
                // Bucket-wide search results are read-only (from other folders); no selection there.
                when {
                    bucketSearch -> Unit
                    state.selecting -> viewModel.toggleSelect(item)
                    else -> viewModel.enterSelection(item)
                }
            }

            when {
                state.isLoading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.items.isEmpty() && state.query.isNotBlank() ->
                    NoMatches(query = state.query, modifier = Modifier.align(Alignment.Center))

                state.items.isEmpty() ->
                    EmptyState(modifier = Modifier.align(Alignment.Center))

                grid -> FileGrid(
                    items = state.items,
                    selectedKeys = state.selected,
                    selecting = state.selecting,
                    showPath = bucketSearch,
                    thumbnailFor = viewModel::localPdf,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )

                else -> FileList(
                    items = state.items,
                    selectedKeys = state.selected,
                    selecting = state.selecting,
                    showPath = bucketSearch,
                    thumbnailFor = viewModel::localPdf,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }

            if (state.isWorking) {
                CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onConfirm = { name ->
                showNewFolder = false
                viewModel.createFolder(name)
            },
            onDismiss = { showNewFolder = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = {
                Text(
                    if (selectedItems.size == 1) stringResource(R.string.dialog_delete_one, selectedItems.first().name)
                    else stringResource(R.string.dialog_delete_many, selectedItems.size),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    pendingRename?.let { item ->
        RenameDialog(
            current = item.name,
            isFolder = item is S3Item.Folder,
            onConfirm = { newName ->
                viewModel.rename(item, newName)
                pendingRename = null
            },
            onDismiss = { pendingRename = null },
        )
    }

    shareLinkFor?.let { file ->
        ShareLinkDialog(
            fileName = file.name,
            onPick = { duration ->
                shareLinkFor = null
                viewModel.clearSelection()
                scope.launch {
                    val url = viewModel.shareLink(file, duration)
                    if (url != null) shareTextLink(context, file.name, url)
                }
            },
            onDismiss = { shareLinkFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    canRename: Boolean,
    canDownload: Boolean,
    canLink: Boolean,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDownload: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel_selection))
            }
        },
        title = { Text(stringResource(R.string.browser_selected, count)) },
        actions = {
            if (canLink) {
                IconButton(onClick = onShareLink) { Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.cd_share_link)) }
            }
            if (canRename) {
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename)) }
            }
            if (canDownload) {
                IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.action_download)) }
            }
            IconButton(onClick = onMove) { Icon(Icons.Filled.DriveFileMove, contentDescription = stringResource(R.string.action_move)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            IconButton(onClick = { overflow = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_select_all)) },
                    leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    onClick = {
                        overflow = false
                        onSelectAll()
                    },
                )
            }
        },
    )
}

@Composable
private fun ShareLinkDialog(
    fileName: String,
    onPick: (Duration) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharelink_title)) },
        text = {
            Column {
                Text(stringResource(R.string.sharelink_desc, fileName))
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = { onPick(1.hours) }) { Text(stringResource(R.string.sharelink_1h)) }
                    TextButton(onClick = { onPick(24.hours) }) { Text(stringResource(R.string.sharelink_1d)) }
                    TextButton(onClick = { onPick(7.days) }) { Text(stringResource(R.string.sharelink_7d)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun shareTextLink(context: Context, fileName: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.chooser_share_link)))
}

@Composable
private fun TransferBar(progress: TransferProgress, onCancel: () -> Unit) {
    val overall = if (progress.total > 0) {
        ((progress.done + progress.pct) / progress.total).coerceIn(0f, 1f)
    } else {
        0f
    }
    val verb = stringResource(
        when (progress.kind) {
            TransferKind.UPLOAD -> R.string.transfer_uploading
            TransferKind.DOWNLOAD -> R.string.transfer_downloading
        },
    )
    val counter = if (progress.total > 1) {
        stringResource(R.string.transfer_counter, progress.done + 1, progress.total)
    } else {
        ""
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$verb ${progress.name}$counter",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
            LinearProgressIndicator(
                progress = { overall },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    bucketScope: Boolean,
) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text(stringResource(if (bucketScope) R.string.search_everywhere else R.string.search_this_folder)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Breadcrumb(bucket: String, path: String, onNavigate: (String) -> Unit) {
    // (label, prefix) pairs: bucket root, then each folder segment.
    val crumbs = remember(bucket, path) {
        buildList {
            add((bucket.ifEmpty { "PdfVault" }) to "")
            val trimmed = path.trim('/')
            if (trimmed.isNotEmpty()) {
                var acc = ""
                for (segment in trimmed.split('/')) {
                    acc += "$segment/"
                    add(segment to acc)
                }
            }
        }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (label, prefix) ->
            val isLast = index == crumbs.lastIndex
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = if (isLast) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
                modifier = if (isLast) {
                    Modifier.padding(horizontal = 2.dp)
                } else {
                    Modifier.clickable { onNavigate(prefix) }.padding(horizontal = 2.dp)
                },
            )
        }
    }
}

@Composable
private fun FileList(
    items: List<S3Item>,
    selectedKeys: Set<String>,
    selecting: Boolean,
    showPath: Boolean,
    thumbnailFor: (S3Item.File) -> File?,
    onItemClick: (S3Item) -> Unit,
    onItemLongClick: (S3Item) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { item ->
            FileRow(
                item = item,
                selected = item.key in selectedKeys,
                selecting = selecting,
                showPath = showPath,
                thumbnailFile = (item as? S3Item.File)?.takeIf { it.isPdf }?.let(thumbnailFor),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: S3Item,
    selected: Boolean,
    selecting: Boolean,
    showPath: Boolean,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isPdf = item is S3Item.File && item.isPdf
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            if (isPdf && thumbnailFile != null) {
                PdfThumbnail(thumbnailFile)
            } else {
                Icon(
                    imageVector = when {
                        item is S3Item.Folder -> Icons.Filled.Folder
                        isPdf -> Icons.Filled.PictureAsPdf
                        else -> Icons.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (item is S3Item.File) {
                // In bucket-wide search, show the folder the file lives in; otherwise size + date.
                val text = if (showPath) {
                    item.key.substringBeforeLast('/', "").ifEmpty { stringResource(R.string.browser_root) }
                } else {
                    val date = com.pdfvault.ui.formatEpochSeconds(item.lastModifiedEpochSeconds)
                    val size = com.pdfvault.ui.formatSize(item.size)
                    if (date.isEmpty()) size else "$size • $date"
                }
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            when {
                selecting -> Checkbox(checked = selected, onCheckedChange = null)
                isPdf && thumbnailFile != null -> Icon(
                    Icons.Filled.CloudDone,
                    contentDescription = stringResource(R.string.cd_available_offline),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGrid(
    items: List<S3Item>,
    selectedKeys: Set<String>,
    selecting: Boolean,
    showPath: Boolean,
    thumbnailFor: (S3Item.File) -> File?,
    onItemClick: (S3Item) -> Unit,
    onItemLongClick: (S3Item) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(items, key = { it.key }) { item ->
            GridCell(
                item = item,
                selected = item.key in selectedKeys,
                thumbnailFile = (item as? S3Item.File)?.takeIf { it.isPdf }?.let(thumbnailFor),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    item: S3Item,
    selected: Boolean,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isPdf = item is S3Item.File && item.isPdf
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (isPdf && thumbnailFile != null) {
                PdfThumbnail(thumbnailFile, width = 84.dp, height = 108.dp)
            } else {
                Box(
                    modifier = Modifier.width(84.dp).padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            item is S3Item.Folder -> Icons.Filled.Folder
                            isPdf -> Icons.Filled.PictureAsPdf
                            else -> Icons.Filled.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(2.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun RenameDialog(
    current: String,
    isFolder: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isFolder) R.string.rename_folder_title else R.string.rename_file_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && name.trim() != current,
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NewFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_new_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_folder_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SortMenuItem(
    state: BrowserUiState,
    mode: SortMode,
    label: String,
    onSelect: (SortMode) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (state.sortMode == mode) {
                Icon(
                    imageVector = if (state.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(if (state.sortAscending) R.string.cd_sort_ascending else R.string.cd_sort_descending),
                )
            }
        },
        onClick = { onSelect(mode) },
    )
}

@Composable
private fun NoMatches(query: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.browser_no_matches, query), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.browser_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.browser_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun destKeyFor(item: S3Item, destPrefix: String): String =
    if (item is S3Item.Folder) "$destPrefix${item.name}/" else "$destPrefix${item.name}"

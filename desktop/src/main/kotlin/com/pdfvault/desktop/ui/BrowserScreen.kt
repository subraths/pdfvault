package com.pdfvault.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.data.AppStorage
import com.pdfvault.desktop.model.S3Item
import com.pdfvault.desktop.s3.S3Repository
import com.pdfvault.desktop.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private enum class SortField(val label: String) { NAME("Name"), DATE("Date modified"), SIZE("Size") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    repository: S3Repository,
    onOpenPdf: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val stack = remember { mutableStateListOf("") }
    val currentPrefix = stack.last()

    var items by remember { mutableStateOf<List<S3Item>>(emptyList()) }
    var allFiles by remember { mutableStateOf<List<S3Item.File>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    var sortField by remember { mutableStateOf(SortField.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var showSort by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchBucket by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var moving by remember { mutableStateOf<List<S3Item>>(emptyList()) }
    var hiddenKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<S3Item?>(null) }
    var shareTarget by remember { mutableStateOf<S3Item.File?>(null) }

    LaunchedEffect(currentPrefix, reloadKey) {
        loading = true; error = null
        runCatching { repository.listChildren(currentPrefix) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Couldn't list files." }
        loading = false
    }

    // Whole-bucket search needs the full object list; fetch it lazily the first time it's used.
    LaunchedEffect(searchBucket, searchOpen) {
        if (searchOpen && searchBucket && allFiles == null) {
            runCatching { repository.listAllFiles() }.onSuccess { allFiles = it }
        }
    }

    fun reload() { reloadKey++; allFiles = null }

    fun navigateTo(depth: Int) {
        while (stack.size > depth + 1) stack.removeAt(stack.lastIndex)
        selected = emptySet()
    }

    fun requestDelete(toDelete: List<S3Item>) {
        if (toDelete.isEmpty()) return
        val keys = toDelete.map { it.key }.toSet()
        hiddenKeys = hiddenKeys + keys
        selected = emptySet()
        scope.launch {
            val label = if (toDelete.size == 1) "Deleted \"${toDelete.first().name}\"" else "Deleted ${toDelete.size} items"
            val result = snackbarHostState.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                hiddenKeys = hiddenKeys - keys
            } else {
                working = true
                toDelete.forEach { runCatching { repository.delete(it) }.onFailure { e -> error = e.message } }
                working = false
                hiddenKeys = hiddenKeys - keys
                reload()
            }
        }
    }

    fun doMoveHere() {
        val toMove = moving
        moving = emptyList()
        scope.launch {
            working = true; error = null
            for (item in toMove) {
                val dest = when (item) {
                    is S3Item.Folder -> currentPrefix + item.name + "/"
                    is S3Item.File -> currentPrefix + item.name
                }
                if (dest != item.key) runCatching { repository.move(item, dest) }.onFailure { error = it.message }
            }
            working = false
            reload()
        }
    }

    val selectedItems = remember(items, selected, allFiles) {
        (items + allFiles.orEmpty()).filter { it.key in selected }.distinctBy { it.key }
    }

    val displayed = remember(items, allFiles, query, searchOpen, searchBucket, sortField, ascending, hiddenKeys) {
        val source = if (searchOpen && searchBucket) allFiles.orEmpty() else items
        val filtered = source
            .filter { it.key !in hiddenKeys }
            .filter { !searchOpen || query.isBlank() || it.name.contains(query, ignoreCase = true) }
        sortItems(filtered, sortField, ascending)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searchOpen) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text(if (searchBucket) "Search whole bucket" else "Search this folder") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(repository.bucket, style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon = {
                        if (searchOpen) {
                            IconButton(onClick = { searchOpen = false; query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        } else if (stack.size > 1) {
                            IconButton(onClick = { navigateTo(stack.lastIndex - 1) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { showSort = true }) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                SortField.entries.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.label) },
                                        leadingIcon = {
                                            if (field == sortField) Icon(Icons.Filled.Check, contentDescription = null)
                                            else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            if (sortField == field) ascending = !ascending else { sortField = field; ascending = true }
                                            showSort = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { gridView = !gridView }) {
                            Icon(
                                if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = if (gridView) "List view" else "Grid view",
                            )
                        }
                        IconButton(onClick = { showNewFolder = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                        IconButton(onClick = {
                            val files = FileChoosers.openPdfs()
                            if (files.isNotEmpty()) scope.launch {
                                working = true; error = null
                                var failures = 0
                                for (file in files) {
                                    runCatching { repository.upload(currentPrefix + file.name, file) }.onFailure { failures++ }
                                }
                                working = false
                                if (failures > 0) error = "Failed to upload $failures file(s)."
                                reload()
                                Notifier.notify(
                                    "PdfVault",
                                    if (failures == 0) "Uploaded ${files.size} file(s)" else "Uploaded with $failures failure(s)",
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Upload, contentDescription = "Upload PDFs")
                        }
                        IconButton(onClick = { reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    },
                )

                when {
                    selected.isNotEmpty() -> SelectionBar(
                        count = selected.size,
                        onDownload = {
                            val dir = FileChoosers.chooseDirectory()
                            if (dir != null) {
                                val toGet = selectedItems.filterIsInstance<S3Item.File>()
                                selected = emptySet()
                                scope.launch {
                                    working = true
                                    toGet.forEach { f ->
                                        runCatching { withContext(Dispatchers.IO) { repository.download(f.key, java.io.File(dir, f.name)) } }
                                            .onFailure { error = it.message }
                                    }
                                    working = false
                                    Notifier.notify("PdfVault", "Downloaded ${toGet.size} file(s) to ${dir.name}")
                                }
                            }
                        },
                        onMove = { moving = selectedItems; selected = emptySet() },
                        onDelete = { requestDelete(selectedItems) },
                        onClear = { selected = emptySet() },
                    )

                    moving.isNotEmpty() -> MoveBar(
                        count = moving.size,
                        destination = "/" + currentPrefix,
                        onMoveHere = { doMoveHere() },
                        onCancel = { moving = emptyList() },
                    )

                    searchOpen -> SearchScopeRow(searchBucket) { searchBucket = it }

                    else -> Breadcrumb(stack, onNavigate = { navigateTo(it) })
                }
            }
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            when {
                loading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                displayed.isEmpty() -> Text(
                    if (searchOpen && query.isNotBlank()) "No matches for \"$query\"" else "This folder is empty.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                gridView -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(displayed, key = { it.key }) { item ->
                        FileCard(
                            item = item,
                            selected = item.key in selected,
                            selectionActive = selected.isNotEmpty(),
                            onOpen = { openItem(item, stack, onOpenPdf) },
                            onToggleSelect = { selected = selected.toggle(item.key) },
                        )
                    }
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(displayed, key = { it.key }) { item ->
                        FileRow(
                            item = item,
                            selected = item.key in selected,
                            selectionActive = selected.isNotEmpty(),
                            showFolderPath = searchOpen && searchBucket,
                            onOpen = { openItem(item, stack, onOpenPdf) },
                            onToggleSelect = { selected = selected.toggle(item.key) },
                            onDownload = {
                                val dest = if (item is S3Item.File) FileChoosers.savePath(item.name) else null
                                if (dest != null) {
                                    scope.launch {
                                        working = true
                                        runCatching { withContext(Dispatchers.IO) { repository.download(item.key, dest) } }
                                            .onFailure { error = it.message }
                                        working = false
                                    }
                                }
                            },
                            onShare = { if (item is S3Item.File) shareTarget = item },
                            onRename = { renameTarget = item },
                            onDelete = { requestDelete(listOf(item)) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            if (working) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmText = "Create",
            onConfirm = { name ->
                showNewFolder = false
                val clean = name.trim().trim('/')
                if (clean.isNotEmpty()) scope.launch {
                    working = true
                    runCatching { repository.createFolder(currentPrefix + clean) }.onFailure { error = it.message }
                    working = false
                    reload()
                }
            },
            onDismiss = { showNewFolder = false },
        )
    }

    renameTarget?.let { item ->
        NameDialog(
            title = if (item is S3Item.Folder) "Rename folder" else "Rename file",
            label = "Name",
            initial = item.name,
            confirmText = "Rename",
            onConfirm = { name ->
                renameTarget = null
                val clean = name.trim().trim('/')
                if (clean.isNotEmpty() && clean != item.name) scope.launch {
                    working = true
                    val newKey = if (item is S3Item.Folder) currentPrefix + clean + "/" else currentPrefix + clean
                    runCatching { repository.move(item, newKey) }.onFailure { error = it.message }
                    working = false
                    reload()
                }
            },
            onDismiss = { renameTarget = null },
        )
    }

    shareTarget?.let { file ->
        ShareDialog(
            fileName = file.name,
            onShare = { duration ->
                shareTarget = null
                scope.launch {
                    working = true
                    runCatching { repository.presignedUrl(file.key, duration) }
                        .onSuccess {
                            clipboard.setText(AnnotatedString(it))
                            snackbarHostState.showSnackbar("Share link copied to clipboard")
                        }
                        .onFailure { error = it.message }
                    working = false
                }
            },
            onDismiss = { shareTarget = null },
        )
    }
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key

private fun openItem(item: S3Item, stack: MutableList<String>, onOpenPdf: (String) -> Unit) {
    when (item) {
        is S3Item.Folder -> stack.add(item.key)
        is S3Item.File -> if (item.isPdf) onOpenPdf(item.key)
    }
}

private fun sortItems(list: List<S3Item>, field: SortField, ascending: Boolean): List<S3Item> {
    val folders = list.filterIsInstance<S3Item.Folder>().sortedBy { it.name.lowercase() }
    val files = list.filterIsInstance<S3Item.File>()
    val sortedFiles = when (field) {
        SortField.NAME -> files.sortedBy { it.name.lowercase() }
        SortField.DATE -> files.sortedBy { it.lastModifiedEpochSeconds }
        SortField.SIZE -> files.sortedBy { it.size }
    }.let { if (ascending) it else it.reversed() }
    // Folders always lead; only files carry the chosen ordering.
    return folders + sortedFiles
}

@Composable
private fun Breadcrumb(stack: List<String>, onNavigate: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stack.forEachIndexed { index, prefix ->
            val label = if (index == 0) "Home" else prefix.trimEnd('/').substringAfterLast('/')
            if (index > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (index == stack.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNavigate(index) }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchScopeRow(bucket: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = !bucket, onClick = { onChange(false) }, label = { Text("This folder") })
        FilterChip(selected = bucket, onClick = { onChange(true) }, label = { Text("Whole bucket") })
    }
}

@Composable
private fun SelectionBar(count: Int, onDownload: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
            Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = "Download") }
            IconButton(onClick = onMove) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
private fun MoveBar(count: Int, destination: String, onMoveHere: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel move") }
            Text(
                "Moving $count to $destination",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onMoveHere) { Text("Move here") }
        }
    }
}

@Composable
private fun FileRow(
    item: S3Item,
    selected: Boolean,
    selectionActive: Boolean,
    showFolderPath: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPdf = item is S3Item.File && item.isPdf
    val offline = remember(item.key) { item is S3Item.File && AppStorage.cacheFileFor(item.key).let { it.exists() && it.length() > 0 } }
    var menu by remember { mutableStateOf(false) }
    ListItem(
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ListItemDefaults.colors(),
        modifier = Modifier.clickable { if (selectionActive) onToggleSelect() else onOpen() },
        leadingContent = {
            if (selectionActive) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            } else {
                Icon(iconFor(item), contentDescription = null, tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (item is S3Item.File) {
                val folder = if (showFolderPath) item.key.substringBeforeLast('/', "") else ""
                Text(listOf(formatSize(item.size), folder).filter { it.isNotEmpty() }.joinToString("  •  "))
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offline) Icon(Icons.Filled.OfflinePin, contentDescription = "Available offline", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                if (item is S3Item.File) {
                    IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = "Download") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.SwapVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Select") },
                            leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = { menu = false; onToggleSelect() },
                        )
                        if (item is S3Item.File) {
                            DropdownMenuItem(
                                text = { Text("Share link") },
                                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                                onClick = { menu = false; onShare() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FileCard(
    item: S3Item,
    selected: Boolean,
    selectionActive: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val isPdf = item is S3Item.File && item.isPdf
    Surface(
        tonalElevation = if (selected) 6.dp else 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(150.dp).clickable { if (selectionActive) onToggleSelect() else onOpen() },
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
            )
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    iconFor(item),
                    contentDescription = null,
                    tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    item.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                if (item is S3Item.File) {
                    Text(formatSize(item.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, confirmText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ShareDialog(fileName: String, onShare: (Duration) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share link") },
        text = {
            Column {
                Text("Create a temporary download link for \"$fileName\". Anyone with the link can download it until it expires.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onShare(1.hours) }) { Text("1 hour") }
                    TextButton(onClick = { onShare(1.days) }) { Text("1 day") }
                    TextButton(onClick = { onShare(7.days) }) { Text("7 days") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun iconFor(item: S3Item) = when {
    item is S3Item.Folder -> Icons.Filled.Folder
    item is S3Item.File && item.isPdf -> Icons.Filled.PictureAsPdf
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024; unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}

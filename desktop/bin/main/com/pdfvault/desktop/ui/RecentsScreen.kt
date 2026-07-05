package com.pdfvault.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.data.AppStorage
import com.pdfvault.desktop.data.ReaderPreferences
import com.pdfvault.desktop.data.RecentItem
import com.pdfvault.desktop.data.RecentsStore
import com.pdfvault.desktop.pdf.renderPdfThumbnail
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(onOpenPdf: (String) -> Unit) {
    val items by RecentsStore.recents.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Pull the latest recents from the backend when the tab opens (no-op if signed out).
    androidx.compose.runtime.LaunchedEffect(Unit) { com.pdfvault.desktop.sync.SyncManager.syncAll() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Recently opened") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { RecentsStore.clear() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (items.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No recent PDFs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "PDFs you open from Files will show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.objectKey }) { item ->
                        RecentRow(
                            item = item,
                            onOpen = { onOpenPdf(item.objectKey) },
                            onRemove = {
                                RecentsStore.remove(item.objectKey)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed from recents",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) RecentsStore.restore(item)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(item: RecentItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    val cacheFile = remember(item.objectKey) { AppStorage.cacheFileFor(item.objectKey) }
    val thumbnail by produceState<ImageBitmap?>(null, item.objectKey) {
        value = renderPdfThumbnail(cacheFile)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(width = 44.dp, height = 58.dp), contentAlignment = Alignment.Center) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val folder = item.objectKey.substringBeforeLast('/', "")
            val meta = listOf(formatRelative(item.openedAtMillis), folder).filter { it.isNotEmpty() }.joinToString("  •  ")
            if (meta.isNotEmpty()) {
                Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.totalPages > 0) {
                val page = (ReaderPreferences.lastPage(item.objectKey) + 1).coerceIn(1, item.totalPages)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { page / item.totalPages.toFloat() }, modifier = Modifier.width(180.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("$page / ${item.totalPages}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatRelative(millis: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
    return when {
        d.toMinutes() < 1 -> "Just now"
        d.toHours() < 1 -> "${d.toMinutes()} min ago"
        d.toDays() < 1 -> "${d.toHours()} h ago"
        d.toDays() < 7 -> "${d.toDays()} d ago"
        else -> "${d.toDays() / 7} wk ago"
    }
}

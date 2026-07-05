package com.pdfvault.ui.recents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfvault.R
import com.pdfvault.data.RecentItem
import com.pdfvault.ui.PdfThumbnail
import com.pdfvault.ui.formatRelativeMillis
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    onOpenPdf: (String) -> Unit,
    viewModel: RecentsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val removedMessage = stringResource(R.string.recents_removed)
    val undoLabel = stringResource(R.string.action_undo)

    // Pull the latest recents from the backend when the tab opens (no-op if signed out).
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.syncFromCloud() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recents_title)) },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recents_clear_history_cd))
                        }
                    }
                },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyRecents(Modifier.align(Alignment.Center))
            } else {
                val groups = remember(items) { groupByDate(items) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (labelRes, groupItems) ->
                        item(key = "header-$labelRes") { GroupHeader(labelRes) }
                        items(groupItems, key = { it.objectKey }) { item ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.remove(item.objectKey)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = removedMessage,
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                                // A shorter swipe (a third of the row) is enough to remove — lighter to trigger.
                                positionalThreshold = { distance -> distance * 0.33f },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
                            ) {
                                RecentRow(
                                    item = item,
                                    lastPage = viewModel.lastPage(item.objectKey),
                                    thumbnailFile = viewModel.localPdf(item.objectKey),
                                    onClick = { onOpenPdf(item.objectKey) },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(@StringRes labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    // Show the delete affordance on whichever edge the row is being swiped toward.
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.recents_remove_cd),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun RecentRow(
    item: RecentItem,
    lastPage: Int,
    thumbnailFile: File?,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (thumbnailFile != null) {
                PdfThumbnail(thumbnailFile)
            } else {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                val folder = item.objectKey.substringBeforeLast('/', "")
                val meta = listOf(formatRelativeMillis(item.openedAtMillis), folder)
                    .filter { it.isNotEmpty() }.joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.totalPages > 0) {
                    val page = (lastPage + 1).coerceIn(1, item.totalPages)
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            progress = { page / item.totalPages.toFloat() },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.page_fraction, page, item.totalPages),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
    )
}

/** Buckets recents (already newest-first) into Today / Yesterday / Earlier this week / Earlier. */
private fun groupByDate(items: List<RecentItem>): List<Pair<Int, List<RecentItem>>> {
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 24L * 60 * 60 * 1000
    val order = listOf(
        R.string.recents_today,
        R.string.recents_yesterday,
        R.string.recents_earlier_week,
        R.string.recents_earlier,
    )
    return items.groupBy { item ->
        when {
            item.openedAtMillis >= startOfToday -> R.string.recents_today
            item.openedAtMillis >= startOfToday - dayMs -> R.string.recents_yesterday
            item.openedAtMillis >= startOfToday - 6 * dayMs -> R.string.recents_earlier_week
            else -> R.string.recents_earlier
        }
    }.toList().sortedBy { order.indexOf(it.first) }
}

@Composable
private fun EmptyRecents(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.recents_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.recents_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

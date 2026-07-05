package com.pdfvault.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.pdfvault.R
import com.pdfvault.ui.browser.BrowserScreen
import com.pdfvault.ui.recents.RecentsScreen
import com.pdfvault.ui.settings.SettingsScreen

private enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    RECENTS(R.string.tab_recent, Icons.Filled.History),
    FILES(R.string.tab_files, Icons.Filled.Folder),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/**
 * Bottom-navigation host holding the three top-level tabs. The tab screens share this
 * destination's ViewModelStore, so state (e.g. the browser's folder position) survives
 * switching tabs. The PDF viewer opens above this over the whole screen (no bottom bar).
 */
@Composable
fun MainScreen(
    onOpenPdf: (String) -> Unit,
    onSignedOut: () -> Unit,
    onAddAccount: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.RECENTS) }

    // On a non-default tab, device back returns to Recents instead of leaving the app.
    BackHandler(enabled = tab != MainTab.RECENTS) { tab = MainTab.RECENTS }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    val label = stringResource(entry.label)
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                MainTab.RECENTS -> RecentsScreen(onOpenPdf = onOpenPdf)
                MainTab.FILES -> BrowserScreen(onOpenPdf = onOpenPdf)
                MainTab.SETTINGS -> SettingsScreen(onSignedOut = onSignedOut, onAddAccount = onAddAccount)
            }
        }
    }
}

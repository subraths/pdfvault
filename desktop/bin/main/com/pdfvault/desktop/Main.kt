package com.pdfvault.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pdfvault.desktop.data.AuthStore
import com.pdfvault.desktop.data.CredentialStore
import com.pdfvault.desktop.data.ThemeMode
import com.pdfvault.desktop.data.UiPreferences
import com.pdfvault.desktop.s3.S3Repository
import com.pdfvault.desktop.sync.SyncManager
import com.pdfvault.desktop.ui.BrowserScreen
import com.pdfvault.desktop.ui.CloudDialog
import com.pdfvault.desktop.ui.FileChoosers
import com.pdfvault.desktop.ui.OpenTarget
import com.pdfvault.desktop.ui.ReaderController
import com.pdfvault.desktop.ui.ReaderScreen
import com.pdfvault.desktop.ui.RecentsScreen
import com.pdfvault.desktop.ui.SettingsScreen
import com.pdfvault.desktop.ui.SetupScreen
import com.pdfvault.desktop.ui.openTargetFromKey
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private enum class Tab { RECENTS, FILES, SETTINGS }

/** App-level actions reachable from window-level keyboard shortcuts. */
private class AppActions {
    var openLocal: () -> Unit = {}
}

fun main(args: Array<String>) = application {
    // Open a PDF passed on the command line (file association / "open with").
    val initialLocal = args.firstOrNull { it.endsWith(".pdf", ignoreCase = true) }
        ?.let { File(it) }?.takeIf { it.exists() }

    val bounds = UiPreferences.windowBounds
    val windowState = rememberWindowState(
        position = if (bounds.x >= 0) WindowPosition(bounds.x.dp, bounds.y.dp) else WindowPosition(Alignment.Center),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )

    val appActions = remember { AppActions() }

    fun saveBoundsAndQuit() {
        val size = windowState.size
        val pos = windowState.position as? WindowPosition.Absolute
        UiPreferences.setWindowBounds(
            x = pos?.x?.value?.toInt() ?: -1,
            y = pos?.y?.value?.toInt() ?: -1,
            width = size.width.value.toInt(),
            height = size.height.value.toInt(),
        )
        exitApplication()
    }

    Window(
        onCloseRequest = ::saveBoundsAndQuit,
        title = "PdfVault",
        state = windowState,
        // App-level shortcuts (the reader handles its own keys when focused).
        onPreviewKeyEvent = { ev ->
            when {
                ev.type != KeyEventType.KeyDown -> false
                ev.isCtrlPressed && ev.key == Key.O -> { appActions.openLocal(); true }
                ev.isCtrlPressed && ev.key == Key.Q -> { saveBoundsAndQuit(); true }
                else -> false
            }
        },
    ) {
        App(
            initialLocal = initialLocal,
            appActions = appActions,
            onToggleFullscreen = {
                windowState.placement =
                    if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                    else WindowPlacement.Fullscreen
            },
        )
    }
}

@Composable
private fun FrameWindowScope.App(initialLocal: File?, appActions: AppActions, onToggleFullscreen: () -> Unit) {
    val credentials = remember { CredentialStore() }
    var repository by remember { mutableStateOf(credentials.load()?.let { S3Repository(it) }) }
    var opened by remember { mutableStateOf<OpenTarget?>(initialLocal?.let { OpenTarget.Local(it) }) }
    var tab by remember { mutableStateOf(Tab.RECENTS) }
    var themeMode by remember { mutableStateOf(UiPreferences.themeMode) }
    var showCloud by remember { mutableStateOf(false) }
    val controller = remember { ReaderController() }
    val syncScope = rememberCoroutineScope()

    fun rebuildSession() { repository = credentials.load()?.let { S3Repository(it) } }

    // On launch, if already signed in, pull accounts + recents; adopt a cloud account if we have
    // none. Fresh install (no S3 config, not signed in): lead with sign-in / create-account so an
    // existing user restores everything without touching S3 keys again. runCatching(Throwable)
    // because cloud sync must never crash the app — an uncaught throw here (e.g. a missing JDK
    // module in the packaged runtime, or network stack failure) killed the whole window.
    LaunchedEffect(Unit) {
        runCatching {
            when {
                AuthStore.isSignedIn -> { if (SyncManager.syncAll()) rebuildSession() }
                repository == null && SyncManager.enabled -> showCloud = true
            }
        }
    }

    // Fullscreen is a window-level action, so wire it here rather than inside the reader.
    SideEffect { controller.onToggleFullscreen = onToggleFullscreen }

    // Drag a PDF from the file manager onto the window to open it in the reader.
    DisposableEffect(Unit) {
        val dropTarget = DropTarget(window, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                runCatching {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val data = event.transferable.getTransferData(DataFlavor.javaFileListFlavor)
                    val pdf = (data as? List<*>)?.filterIsInstance<File>()
                        ?.firstOrNull { it.name.endsWith(".pdf", ignoreCase = true) && it.exists() }
                    if (pdf != null) opened = OpenTarget.Local(pdf)
                    event.dropComplete(pdf != null)
                }.onFailure { event.dropComplete(false) }
            }
        })
        onDispose {
            dropTarget.isActive = false
            window.dropTarget = null
        }
    }

    fun setTheme(mode: ThemeMode) { UiPreferences.themeMode = mode; themeMode = mode }
    fun openLocal() { FileChoosers.openSinglePdf()?.let { opened = OpenTarget.Local(it) } }

    // Expose app-level actions to the window's keyboard shortcuts (Ctrl+O).
    SideEffect { appActions.openLocal = { openLocal() } }

    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val repo = repository
            when {
                // The reader takes over the whole window; works for local files even before S3 setup.
                opened != null -> ReaderScreen(
                    repository = repo,
                    target = opened!!,
                    controller = controller,
                    onBack = { opened = null },
                )

                repo == null -> SetupScreen(
                    credentials = credentials,
                    onConfigured = { config ->
                        repository = S3Repository(config)
                        // First-time setup while signed in: store the account (secret encrypted)
                        // in the cloud so signing in anywhere restores it without re-entering keys.
                        syncScope.launch { runCatching { SyncManager.syncAll() } }
                    },
                )

                else -> Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = tab == Tab.RECENTS,
                            onClick = { tab = Tab.RECENTS },
                            icon = { Icon(Icons.Filled.History, contentDescription = "Recent") },
                            label = { Text("Recent") },
                        )
                        NavigationRailItem(
                            selected = tab == Tab.FILES,
                            onClick = { tab = Tab.FILES },
                            icon = { Icon(Icons.Filled.Folder, contentDescription = "Files") },
                            label = { Text("Files") },
                        )
                        NavigationRailItem(
                            selected = tab == Tab.SETTINGS,
                            onClick = { tab = Tab.SETTINGS },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                        )
                    }
                    when (tab) {
                        Tab.RECENTS -> RecentsScreen(onOpenPdf = { key -> opened = openTargetFromKey(key) })
                        Tab.FILES -> BrowserScreen(
                            repository = repo,
                            onOpenPdf = { key -> opened = OpenTarget.Remote(key) },
                            onSignOut = {
                                SyncManager.signOut()
                                credentials.clear()
                                repo.close()
                                repository = null
                                showCloud = true
                            },
                        )
                        Tab.SETTINGS -> SettingsScreen(
                            themeMode = themeMode,
                            accountLabel = credentials.load()?.let { "${it.bucket}  ·  ${it.region}" },
                            onSetTheme = { setTheme(it) },
                            onOpenLocal = { openLocal() },
                            onCloud = { showCloud = true },
                            onSignOut = {
                                // Full sign-out: cloud session + local S3 config. The account (and
                                // its S3 keys, encrypted) stays in the cloud — signing back in with
                                // email + password restores everything.
                                SyncManager.signOut()
                                credentials.clear()
                                repo.close()
                                repository = null
                                showCloud = true
                            },
                        )
                    }
                }
            }

            if (showCloud) {
                CloudDialog(
                    onDismiss = { showCloud = false },
                    onAccountImported = { rebuildSession() },
                )
            }
        }
    }
}


package com.pdfvault.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
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
import com.pdfvault.desktop.ui.SetupScreen
import com.pdfvault.desktop.ui.openTargetFromKey
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private enum class Tab { RECENTS, FILES }

fun main(args: Array<String>) = application {
    // Open a PDF passed on the command line (file association / "open with").
    val initialLocal = args.firstOrNull { it.endsWith(".pdf", ignoreCase = true) }
        ?.let { File(it) }?.takeIf { it.exists() }

    val bounds = UiPreferences.windowBounds
    val windowState = rememberWindowState(
        position = if (bounds.x >= 0) WindowPosition(bounds.x.dp, bounds.y.dp) else WindowPosition(Alignment.Center),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )

    Window(
        onCloseRequest = {
            val size = windowState.size
            val pos = windowState.position as? WindowPosition.Absolute
            UiPreferences.setWindowBounds(
                x = pos?.x?.value?.toInt() ?: -1,
                y = pos?.y?.value?.toInt() ?: -1,
                width = size.width.value.toInt(),
                height = size.height.value.toInt(),
            )
            exitApplication()
        },
        title = "PdfVault",
        state = windowState,
    ) {
        App(
            initialLocal = initialLocal,
            onQuit = ::exitApplication,
            onToggleFullscreen = {
                windowState.placement =
                    if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                    else WindowPlacement.Fullscreen
            },
        )
    }
}

@Composable
private fun FrameWindowScope.App(initialLocal: File?, onQuit: () -> Unit, onToggleFullscreen: () -> Unit) {
    val credentials = remember { CredentialStore() }
    var repository by remember { mutableStateOf(credentials.load()?.let { S3Repository(it) }) }
    var opened by remember { mutableStateOf<OpenTarget?>(initialLocal?.let { OpenTarget.Local(it) }) }
    var tab by remember { mutableStateOf(Tab.RECENTS) }
    var themeMode by remember { mutableStateOf(UiPreferences.themeMode) }
    var showCloud by remember { mutableStateOf(false) }
    val controller = remember { ReaderController() }

    fun rebuildSession() { repository = credentials.load()?.let { S3Repository(it) } }

    // On launch, if already signed in, pull accounts + recents; adopt a cloud account if we have none.
    LaunchedEffect(Unit) {
        if (AuthStore.isSignedIn && SyncManager.syncAll()) rebuildSession()
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

    AppMenuBar(
        controller = controller,
        themeMode = themeMode,
        onOpenLocal = { openLocal() },
        onSetTheme = { setTheme(it) },
        onCloud = { showCloud = true },
        onQuit = onQuit,
    )

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
                    onConfigured = { config -> repository = S3Repository(config) },
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
                            selected = false,
                            onClick = {
                                credentials.clear()
                                repo.close()
                                repository = null
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out") },
                            label = { Text("Sign out") },
                        )
                    }
                    when (tab) {
                        Tab.RECENTS -> RecentsScreen(onOpenPdf = { key -> opened = openTargetFromKey(key) })
                        Tab.FILES -> BrowserScreen(
                            repository = repo,
                            onOpenPdf = { key -> opened = OpenTarget.Remote(key) },
                            onSignOut = {
                                credentials.clear()
                                repo.close()
                                repository = null
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

@Composable
private fun FrameWindowScope.AppMenuBar(
    controller: ReaderController,
    themeMode: ThemeMode,
    onOpenLocal: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onCloud: () -> Unit,
    onQuit: () -> Unit,
) {
    val has = controller.hasDocument
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Open…", onClick = onOpenLocal, shortcut = KeyShortcut(Key.O, ctrl = true))
            Item("Print…", enabled = has, onClick = { controller.onPrint() }, shortcut = KeyShortcut(Key.P, ctrl = true))
            Separator()
            Item("Open in Default Viewer", enabled = has, onClick = { controller.onOpenExternal() })
            Item("Reveal in File Manager", enabled = has, onClick = { controller.onReveal() })
            Separator()
            Item("Quit", onClick = onQuit, shortcut = KeyShortcut(Key.Q, ctrl = true))
        }
        Menu("View", mnemonic = 'V') {
            Item("Zoom In", enabled = has, onClick = { controller.onZoomIn() }, shortcut = KeyShortcut(Key.Equals, ctrl = true))
            Item("Zoom Out", enabled = has, onClick = { controller.onZoomOut() }, shortcut = KeyShortcut(Key.Minus, ctrl = true))
            Item("Reset Zoom", enabled = has, onClick = { controller.onZoomReset() }, shortcut = KeyShortcut(Key.Zero, ctrl = true))
            Separator()
            Item("Fit Width", enabled = has, onClick = { controller.onFitWidth() })
            Item("Rotate Right", enabled = has, onClick = { controller.onRotate() }, shortcut = KeyShortcut(Key.R, ctrl = true))
            Separator()
            Item("Page Thumbnails", enabled = has, onClick = { controller.onToggleThumbnails() })
            Item("Fullscreen", enabled = has, onClick = { controller.onToggleFullscreen() }, shortcut = KeyShortcut(Key.F11))
            Separator()
            Menu("Theme") {
                RadioButtonItem("System", selected = themeMode == ThemeMode.SYSTEM, onClick = { onSetTheme(ThemeMode.SYSTEM) })
                RadioButtonItem("Light", selected = themeMode == ThemeMode.LIGHT, onClick = { onSetTheme(ThemeMode.LIGHT) })
                RadioButtonItem("Dark", selected = themeMode == ThemeMode.DARK, onClick = { onSetTheme(ThemeMode.DARK) })
            }
        }
        Menu("Go", mnemonic = 'G') {
            Item("Next Page", enabled = has, onClick = { controller.onNextPage() })
            Item("Previous Page", enabled = has, onClick = { controller.onPrevPage() })
            Item("First Page", enabled = has, onClick = { controller.onFirstPage() }, shortcut = KeyShortcut(Key.MoveHome, ctrl = true))
            Item("Last Page", enabled = has, onClick = { controller.onLastPage() }, shortcut = KeyShortcut(Key.MoveEnd, ctrl = true))
            Item("Go to Page…", enabled = has, onClick = { controller.onGoToPage() }, shortcut = KeyShortcut(Key.G, ctrl = true))
            Separator()
            Item("Find", enabled = has, onClick = { controller.onFind() }, shortcut = KeyShortcut(Key.F, ctrl = true))
            Item("Toggle Bookmark", enabled = has, onClick = { controller.onBookmark() }, shortcut = KeyShortcut(Key.B, ctrl = true))
        }
        Menu("Cloud", mnemonic = 'C') {
            Item("Sign in / Sync…", onClick = onCloud)
        }
    }
}

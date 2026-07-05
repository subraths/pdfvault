package com.pdfvault.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** What the reader is showing: an object in S3, or a local file on disk. */
sealed interface OpenTarget {
    /** Stable id used for page cache, resume, bookmarks, and recents. */
    val docId: String
    val title: String

    data class Remote(val objectKey: String) : OpenTarget {
        override val docId get() = objectKey
        override val title get() = objectKey.substringAfterLast('/')
    }

    data class Local(val file: File) : OpenTarget {
        override val docId get() = "local:${file.absolutePath}"
        override val title get() = file.name
    }
}

/** Recents store keys local files as "local:<path>"; rebuild the right target from a stored key. */
fun openTargetFromKey(key: String): OpenTarget =
    if (key.startsWith("local:")) OpenTarget.Local(File(key.removePrefix("local:"))) else OpenTarget.Remote(key)

/**
 * Bridges the always-present window menu bar to the currently-open reader. The reader fills in the
 * action callbacks (they close over its live state) and flips [hasDocument]; menu items call the
 * callbacks and enable/disable off [hasDocument]. Callbacks are plain vars so refreshing them each
 * recomposition doesn't trigger one.
 */
class ReaderController {
    var hasDocument by mutableStateOf(false)

    var onZoomIn: () -> Unit = {}
    var onZoomOut: () -> Unit = {}
    var onZoomReset: () -> Unit = {}
    var onFitWidth: () -> Unit = {}
    var onFitPage: () -> Unit = {}
    var onRotate: () -> Unit = {}
    var onToggleTwoUp: () -> Unit = {}
    var onToggleThumbnails: () -> Unit = {}
    var onToggleFullscreen: () -> Unit = {}
    var onNextPage: () -> Unit = {}
    var onPrevPage: () -> Unit = {}
    var onFirstPage: () -> Unit = {}
    var onLastPage: () -> Unit = {}
    var onGoToPage: () -> Unit = {}
    var onFind: () -> Unit = {}
    var onBookmark: () -> Unit = {}
    var onPrint: () -> Unit = {}
    var onOpenExternal: () -> Unit = {}
    var onReveal: () -> Unit = {}
}

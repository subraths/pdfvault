package com.pdfvault.desktop.util

/**
 * Best-effort Linux desktop notifications via `notify-send`. No-ops silently if the tool isn't
 * installed (or on non-Linux desktops), so callers never need to guard.
 */
object Notifier {
    fun notify(title: String, message: String) {
        runCatching { ProcessBuilder("notify-send", "-a", "PdfVault", title, message).start() }
    }
}

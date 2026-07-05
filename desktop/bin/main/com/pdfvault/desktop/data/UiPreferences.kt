package com.pdfvault.desktop.data

import java.io.File
import java.util.Properties

/** App-wide desktop UI preference: which color scheme to use. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted window geometry, in dp. Negative position means "let the OS place it". */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int)

/** Persists desktop-only UI state (theme + main window geometry) to a properties file. */
object UiPreferences {
    private val file = File(AppStorage.configDir, "ui.properties")
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    private fun save() {
        file.outputStream().use { props.store(it, "PdfVault desktop UI") }
    }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(props.getProperty(KEY_THEME) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            props.setProperty(KEY_THEME, value.name)
            save()
        }

    /** Last window geometry, or a sensible default the first time. */
    val windowBounds: WindowBounds
        get() = WindowBounds(
            x = props.getProperty(KEY_X)?.toIntOrNull() ?: -1,
            y = props.getProperty(KEY_Y)?.toIntOrNull() ?: -1,
            width = props.getProperty(KEY_W)?.toIntOrNull() ?: 1100,
            height = props.getProperty(KEY_H)?.toIntOrNull() ?: 780,
        )

    fun setWindowBounds(x: Int, y: Int, width: Int, height: Int) {
        props.setProperty(KEY_X, x.toString())
        props.setProperty(KEY_Y, y.toString())
        props.setProperty(KEY_W, width.toString())
        props.setProperty(KEY_H, height.toString())
        save()
    }

    private const val KEY_THEME = "theme"
    private const val KEY_X = "win_x"
    private const val KEY_Y = "win_y"
    private const val KEY_W = "win_w"
    private const val KEY_H = "win_h"
}

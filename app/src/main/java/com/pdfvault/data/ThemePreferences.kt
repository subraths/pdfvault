package com.pdfvault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

/**
 * Persisted appearance settings, exposed as a [StateFlow] so the whole app (via [MainActivity])
 * recolors live when the user changes them in Settings.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        ThemeSettings(mode = loadMode(), dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true)),
    )
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _settings.update { it.copy(mode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _settings.update { it.copy(dynamicColor = enabled) }
    }

    private fun loadMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}

package com.pdfvault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Auto-backup configuration: which device folder to watch and where to upload it. */
data class BackupSettings(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val folderLabel: String? = null,
    val destPrefix: String = "backup/",
)

/**
 * Persists the auto-backup settings (a SAF tree URI for the watched folder + an S3 destination
 * prefix). Exposed as a [StateFlow] so Settings and the periodic worker see the same state.
 */
@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<BackupSettings> = _settings.asStateFlow()

    fun current(): BackupSettings = _settings.value

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.update { it.copy(enabled = enabled) }
    }

    fun setFolder(treeUri: String?, label: String?) {
        prefs.edit().putString(KEY_URI, treeUri).putString(KEY_LABEL, label).apply()
        _settings.update { it.copy(treeUri = treeUri, folderLabel = label) }
    }

    private fun load() = BackupSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        treeUri = prefs.getString(KEY_URI, null),
        folderLabel = prefs.getString(KEY_LABEL, null),
        destPrefix = prefs.getString(KEY_PREFIX, null) ?: "backup/",
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_URI = "tree_uri"
        const val KEY_LABEL = "folder_label"
        const val KEY_PREFIX = "dest_prefix"
    }
}

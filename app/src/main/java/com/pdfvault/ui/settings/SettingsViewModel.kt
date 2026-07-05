package com.pdfvault.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfvault.BuildConfig
import com.pdfvault.S3SessionManager
import com.pdfvault.data.BackupPreferences
import com.pdfvault.data.BackupSettings
import com.pdfvault.data.ReaderPreferences
import com.pdfvault.data.ReadingDirection
import com.pdfvault.data.RecentsStore
import com.pdfvault.data.ThemeMode
import com.pdfvault.data.ThemePreferences
import com.pdfvault.data.ThemeSettings
import com.pdfvault.data.auth.AuthState
import com.pdfvault.data.model.S3Profile
import com.pdfvault.data.remote.BackendException
import com.pdfvault.sync.SyncManager
import com.pdfvault.transfer.AutoBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: S3SessionManager,
    private val readerPrefs: ReaderPreferences,
    private val themePrefs: ThemePreferences,
    private val recents: RecentsStore,
    private val backupPrefs: BackupPreferences,
    private val autoBackup: AutoBackupManager,
    private val sync: SyncManager,
) : ViewModel() {

    /** Bucket the session is bound to, for display in the Account section. */
    val bucket: String = session.repository?.bucket.orEmpty()

    val appVersion: String = BuildConfig.VERSION_NAME

    // --- Cloud sync ---------------------------------------------------------------------------

    /** Whether a backend URL is configured at build time; false = the sync section is hidden. */
    val syncEnabled: Boolean = sync.enabled
    val authState: StateFlow<AuthState> = sync.authState

    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy: StateFlow<Boolean> = _cloudBusy.asStateFlow()

    private val _cloudError = MutableStateFlow<String?>(null)
    val cloudError: StateFlow<String?> = _cloudError.asStateFlow()

    fun signIn(email: String, password: String) = cloudCall { sync.signIn(email, password) }

    fun register(email: String, password: String) = cloudCall { sync.register(email, password) }

    fun syncNow() = cloudCall { sync.syncAll() }

    fun cloudSignOut() = sync.signOut()

    private fun cloudCall(block: suspend () -> Unit) {
        if (_cloudBusy.value) return
        viewModelScope.launch {
            _cloudBusy.value = true
            _cloudError.value = null
            try {
                block()
            } catch (e: BackendException) {
                _cloudError.value = e.message
            } catch (e: Exception) {
                _cloudError.value = "Couldn't reach the server. Check your connection."
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _direction = MutableStateFlow(readerPrefs.readingDirection)
    val direction: StateFlow<ReadingDirection> = _direction.asStateFlow()

    private val _continuous = MutableStateFlow(readerPrefs.verticalContinuous)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()

    val theme: StateFlow<ThemeSettings> = themePrefs.settings

    val profiles: StateFlow<List<S3Profile>> = session.profiles
    val activeProfileId: StateFlow<String?> = session.activeProfile

    val backup: StateFlow<BackupSettings> = backupPrefs.settings

    init {
        refreshCacheSize()
    }

    fun switchProfile(id: String) = session.switchTo(id)

    fun removeProfile(id: String) = session.removeProfile(id)

    fun setBackupEnabled(enabled: Boolean) {
        backupPrefs.setEnabled(enabled)
        autoBackup.schedule(enabled)
    }

    fun setBackupFolder(treeUri: String?, label: String?) = backupPrefs.setFolder(treeUri, label)

    fun backupNow() = autoBackup.backupNow()

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { session.pdfCacheSizeBytes() }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { session.clearPdfCache() }
            refreshCacheSize()
        }
    }

    fun clearRecents() = recents.clear()

    fun setDirection(direction: ReadingDirection) {
        readerPrefs.readingDirection = direction
        _direction.value = direction
    }

    fun setContinuous(enabled: Boolean) {
        readerPrefs.verticalContinuous = enabled
        _continuous.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) = themePrefs.setMode(mode)

    fun setDynamicColor(enabled: Boolean) = themePrefs.setDynamicColor(enabled)

    /** Wipes stored credentials and the active session. The caller navigates back to setup. */
    fun signOut() = session.reset()
}

package com.pdfvault.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfvault.S3SessionManager
import com.pdfvault.data.auth.AuthStore
import com.pdfvault.data.model.S3Config
import com.pdfvault.data.s3.S3Setup
import com.pdfvault.sync.SyncManager
import com.pdfvault.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    /**
     * Account-first onboarding: when the backend is configured and the user isn't signed in,
     * setup starts with sign-in / create-account. Signing in restores the S3 account stored in
     * the cloud (skipping key entry entirely); creating an account falls through to the
     * one-time S3 key + bucket step below.
     */
    val authRequired: Boolean = false,
    val email: String = "",
    val password: String = "",
    val authBusy: Boolean = false,
    /** True when signed in to the cloud — the S3 keys entered here will be stored in the account. */
    val cloudSignedIn: Boolean = false,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val region: String = "us-east-1",
    val isConnecting: Boolean = false,
    val isWorking: Boolean = false,
    val buckets: List<String>? = null,
    val selectedBucket: String? = null,
    val newBucketName: String = "",
    val error: String? = null,
    val done: Boolean = false,
) {
    val connected: Boolean get() = buckets != null
    val authValid: Boolean get() = email.isNotBlank() && password.length >= 8
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val session: S3SessionManager,
    private val sync: SyncManager,
    private val authStore: AuthStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SetupUiState(
            authRequired = sync.enabled && !authStore.isSignedIn,
            cloudSignedIn = authStore.isSignedIn,
        ),
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onAccessKeyChange(value: String) = _state.update { it.copy(accessKeyId = value, error = null) }
    fun onSecretChange(value: String) = _state.update { it.copy(secretAccessKey = value, error = null) }
    fun onRegionChange(value: String) = _state.update { it.copy(region = value, error = null) }
    fun onNewBucketNameChange(value: String) = _state.update { it.copy(newBucketName = value, error = null) }
    fun onSelectBucket(name: String) = _state.update { it.copy(selectedBucket = name, error = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Signs in to an existing account. The sign-in runs a full sync, so if the account already
     * has an S3 profile stored (from first-time setup on any device) it's restored and setup is
     * finished — no keys asked for again. Otherwise falls through to the key step.
     */
    fun signInCloud() = authCall { sync.signIn(_state.value.email, _state.value.password) }

    /** Creates a new account, then falls through to the one-time S3 key + bucket step. */
    fun registerCloud() = authCall { sync.register(_state.value.email, _state.value.password) }

    private fun authCall(block: suspend () -> Unit) {
        val s = _state.value
        if (!s.authValid) {
            _state.update { it.copy(error = "Enter your email and a password of at least 8 characters.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, error = null) }
            runCatching { block() }.onSuccess {
                // Sign-in/register already synced; if an S3 account came down, we're done.
                if (session.isConfigured) {
                    _state.update { it.copy(authBusy = false, cloudSignedIn = true, done = true) }
                } else {
                    _state.update { it.copy(authBusy = false, cloudSignedIn = true, authRequired = false) }
                }
            }.onFailure { e ->
                _state.update { it.copy(authBusy = false, error = e.userMessage()) }
            }
        }
    }

    /** Validates the keys by listing buckets the credentials can access. */
    fun connect() {
        val s = _state.value
        if (s.accessKeyId.isBlank() || s.secretAccessKey.isBlank()) {
            _state.update { it.copy(error = "Enter both the access key ID and secret access key.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, error = null) }
            runCatching {
                S3Setup.listBuckets(s.accessKeyId.trim(), s.secretAccessKey.trim(), s.region.trim())
            }.onSuccess { list ->
                _state.update {
                    it.copy(isConnecting = false, buckets = list, selectedBucket = list.firstOrNull())
                }
            }.onFailure { e ->
                _state.update { it.copy(isConnecting = false, error = e.userMessage()) }
            }
        }
    }

    fun createBucket() {
        val s = _state.value
        val name = s.newBucketName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a name for the new bucket.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            runCatching {
                S3Setup.createBucket(name, s.accessKeyId.trim(), s.secretAccessKey.trim(), s.region.trim())
            }.onSuccess {
                _state.update {
                    val updated = (it.buckets.orEmpty() + name).distinct().sorted()
                    it.copy(isWorking = false, buckets = updated, selectedBucket = name, newBucketName = "")
                }
            }.onFailure { e ->
                _state.update { it.copy(isWorking = false, error = e.userMessage()) }
            }
        }
    }

    /** Resolves the bucket's real region, persists the config, and verifies connectivity. */
    fun confirm() {
        val s = _state.value
        val bucket = s.selectedBucket
        if (bucket.isNullOrBlank()) {
            _state.update { it.copy(error = "Choose an existing bucket or create a new one.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            val region = S3Setup.resolveBucketRegion(
                bucket = bucket,
                accessKeyId = s.accessKeyId.trim(),
                secretAccessKey = s.secretAccessKey.trim(),
                fallbackRegion = s.region.trim(),
            )
            runCatching {
                session.configure(
                    S3Config(s.accessKeyId.trim(), s.secretAccessKey.trim(), region, bucket),
                )
                // Confirm the bucket is actually reachable before leaving setup.
                session.repository?.listChildren("")
            }.onSuccess {
                // First-time setup while signed in: store the account (secret encrypted) in the
                // cloud, so signing in on any device restores it without re-entering keys.
                runCatching { sync.syncAccounts() }
                _state.update { it.copy(isWorking = false, done = true) }
            }.onFailure { e ->
                session.reset()
                _state.update { it.copy(isWorking = false, error = e.userMessage()) }
            }
        }
    }
}

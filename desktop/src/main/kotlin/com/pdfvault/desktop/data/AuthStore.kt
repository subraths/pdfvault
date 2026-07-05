package com.pdfvault.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

/** Whether the user is signed in to the backend, and if so their email. */
data class AuthState(val signedIn: Boolean, val email: String?)

/**
 * Persists the backend base URL + JWT + email to a properties file under the config dir. The base
 * URL can also come from the `PDFVAULT_BACKEND_URL` env var, which takes precedence.
 *
 * MVP note: like the desktop credential store, the token is stored in plaintext under
 * ~/.config/pdfvault. A production build should use the OS keyring.
 */
object AuthStore {
    private val file = File(AppStorage.configDir, "auth.properties")
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }

    private fun save() = file.outputStream().use { props.store(it, "PdfVault auth (not encrypted)") }

    private val _state = MutableStateFlow(AuthState(props.getProperty(KEY_TOKEN) != null, props.getProperty(KEY_EMAIL)))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val token: String? get() = props.getProperty(KEY_TOKEN)
    val isSignedIn: Boolean get() = token != null

    /** Backend base URL: env var wins, else the value entered in the sign-in dialog, else empty. */
    var baseUrl: String
        get() = System.getenv("PDFVAULT_BACKEND_URL")?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: props.getProperty(KEY_URL).orEmpty()
        set(value) {
            props.setProperty(KEY_URL, value.trim().trimEnd('/'))
            save()
        }

    val enabled: Boolean get() = baseUrl.isNotBlank()

    fun signedIn(token: String, email: String) {
        props.setProperty(KEY_TOKEN, token)
        props.setProperty(KEY_EMAIL, email)
        save()
        _state.value = AuthState(signedIn = true, email = email)
    }

    fun clear() {
        props.remove(KEY_TOKEN)
        props.remove(KEY_EMAIL)
        save()
        _state.value = AuthState(signedIn = false, email = null)
    }

    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"
    private const val KEY_URL = "baseUrl"
}

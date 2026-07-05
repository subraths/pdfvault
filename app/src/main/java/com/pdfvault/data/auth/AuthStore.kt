package com.pdfvault.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user is signed in to the backend, and if so their email. */
data class AuthState(val signedIn: Boolean, val email: String?)

/**
 * Stores the backend JWT (and the signed-in email) encrypted at rest. The token is the app's
 * proof of identity for all authenticated backend calls; clearing it signs the user out locally.
 */
@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { createPrefs(appContext) }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val isSignedIn: Boolean get() = token != null

    fun save(token: String, email: String) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_EMAIL, email).apply()
        _state.value = AuthState(signedIn = true, email = email)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = AuthState(signedIn = false, email = null)
    }

    private fun load(): AuthState {
        val hasToken = prefs.getString(KEY_TOKEN, null) != null
        return AuthState(signedIn = hasToken, email = prefs.getString(KEY_EMAIL, null))
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "pdfvault_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val KEY_TOKEN = "jwt"
        const val KEY_EMAIL = "email"
    }
}

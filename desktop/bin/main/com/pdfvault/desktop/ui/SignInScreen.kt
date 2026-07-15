package com.pdfvault.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.remote.BackendException
import com.pdfvault.desktop.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Full-screen, non-dismissable sign-in gate — signing in (or creating an account) is mandatory.
 * On success [onSignedIn] fires with whether a cloud S3 account was imported; when none was, the
 * app falls through to the one-time S3 key + bucket setup.
 */
@Composable
fun SignInScreen(onSignedIn: (accountImported: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = email.isNotBlank() && password.length >= 8

    fun run(register: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                if (register) SyncManager.register(email, password) else SyncManager.signIn(email, password)
                val imported = SyncManager.syncAll()
                onSignedIn(imported)
            } catch (e: BackendException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Couldn't reach the server. Check your connection. (${e.javaClass.simpleName})"
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Welcome to PdfVault", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign in to restore your library and reading progress, or create an account to " +
                    "get started. New accounts connect their S3 storage once — after that, " +
                    "signing in on any device restores everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                email, { email = it },
                label = { Text("Email") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it },
                label = { Text("Password (min 8 characters)") }, singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { run(register = false) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            OutlinedButton(onClick = { run(register = true) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Create account")
            }
        }
    }
}

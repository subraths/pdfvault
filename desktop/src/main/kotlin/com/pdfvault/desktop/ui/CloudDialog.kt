package com.pdfvault.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.data.AuthStore
import com.pdfvault.desktop.remote.BackendException
import com.pdfvault.desktop.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Sign-in / sync dialog. Reachable from the menu bar regardless of app state (so a fresh install can
 * sign in and pull its S3 account from the cloud). [onAccountImported] fires when a cloud account is
 * adopted locally, so the app can rebuild its S3 session.
 */
@Composable
fun CloudDialog(onDismiss: () -> Unit, onAccountImported: () -> Unit) {
    val authState by SyncManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var url by remember { mutableStateOf(AuthStore.baseUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = url.isNotBlank() && email.isNotBlank() && password.length >= 8

    fun run(register: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                AuthStore.baseUrl = url
                if (register) SyncManager.register(email, password) else SyncManager.signIn(email, password)
                if (SyncManager.syncAll()) onAccountImported()
            } catch (e: BackendException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't reach the server. Check the URL and your connection."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud sync") },
        text = {
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (authState.signedIn) {
                    Text("Signed in as ${authState.email.orEmpty()}")
                    Text(
                        "Your S3 account and recently-opened PDFs sync across your devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { SyncManager.signOut() }, enabled = !busy) { Text("Sign out") }
                        Button(
                            onClick = { scope.launch { busy = true; runCatching { SyncManager.syncAll() }.onSuccess { if (it) onAccountImported() }; busy = false } },
                            enabled = !busy,
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp) else Text("Sync now")
                        }
                    }
                } else {
                    Text(
                        "Sign in to sync your S3 account and recents across devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(url, { url = it }, label = { Text("Backend URL") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { run(register = true) }, enabled = valid && !busy, modifier = Modifier.weight(1f)) { Text("Create account") }
                        Button(onClick = { run(register = false) }, enabled = valid && !busy, modifier = Modifier.weight(1f)) {
                            if (busy) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp) else Text("Sign in")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

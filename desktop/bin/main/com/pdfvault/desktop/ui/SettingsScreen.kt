package com.pdfvault.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pdfvault.desktop.data.ThemeMode
import com.pdfvault.desktop.sync.SyncManager

/**
 * Settings tab: everything that used to live in the window menu bar (theme, cloud account,
 * open-local, S3 sign-out) as a normal themed screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    accountLabel: String?,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenLocal: () -> Unit,
    onCloud: () -> Unit,
    onSignOut: () -> Unit,
) {
    val auth by SyncManager.authState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Appearance")
            ThemeChoice("System default", themeMode == ThemeMode.SYSTEM) { onSetTheme(ThemeMode.SYSTEM) }
            ThemeChoice("Light", themeMode == ThemeMode.LIGHT) { onSetTheme(ThemeMode.LIGHT) }
            ThemeChoice("Dark", themeMode == ThemeMode.DARK) { onSetTheme(ThemeMode.DARK) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Cloud sync")
            Text(
                if (auth.signedIn) "Signed in as ${auth.email.orEmpty()}" else "Not signed in",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Your S3 account and recently-opened PDFs sync across your devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCloud) {
                    Text(if (auth.signedIn) "Manage / Sync…" else "Sign in…")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Storage")
            if (accountLabel != null) {
                Text("Connected to $accountLabel", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onSignOut) { Text("Sign out & switch account") }
            Text(
                "Signs you out everywhere in this app. Your account (and its S3 keys, encrypted) " +
                    "stays in the cloud — sign back in to restore it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Files")
            OutlinedButton(onClick = onOpenLocal) { Text("Open local PDF…") }
            Text(
                "Shortcut: Ctrl+O anywhere in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

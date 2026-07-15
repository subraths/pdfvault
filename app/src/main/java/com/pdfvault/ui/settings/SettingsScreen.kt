package com.pdfvault.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pdfvault.data.auth.AuthState
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfvault.R
import com.pdfvault.data.ReadingDirection
import com.pdfvault.data.ThemeMode
import com.pdfvault.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val continuous by viewModel.continuous.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val cloudBusy by viewModel.cloudBusy.collectAsStateWithLifecycle()
    val cloudError by viewModel.cloudError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val label = DocumentFile.fromTreeUri(context, uri)?.name
            viewModel.setBackupFolder(uri.toString(), label)
        }
    }

    // If the last account is removed, there's nothing to show — return to setup.
    LaunchedEffect(activeProfileId, profiles) {
        if (activeProfileId == null && profiles.isEmpty()) onSignedOut()
    }

    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearRecents by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.section_reader))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_scroll_direction)) },
                supportingContent = { Text(stringResource(R.string.setting_scroll_direction_desc)) },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = direction == ReadingDirection.VERTICAL,
                    onClick = { viewModel.setDirection(ReadingDirection.VERTICAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    label = { Text(stringResource(R.string.dir_vertical)) },
                )
                SegmentedButton(
                    selected = direction == ReadingDirection.HORIZONTAL,
                    onClick = { viewModel.setDirection(ReadingDirection.HORIZONTAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                    label = { Text(stringResource(R.string.dir_horizontal)) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_continuous)) },
                supportingContent = { Text(stringResource(R.string.setting_continuous_desc)) },
                trailingContent = {
                    Switch(checked = continuous, onCheckedChange = { viewModel.setContinuous(it) })
                },
            )

            SectionHeader(stringResource(R.string.section_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_theme)) },
                supportingContent = { Text(stringResource(R.string.setting_theme_desc)) },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = theme.mode == ThemeMode.SYSTEM,
                    onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Filled.BrightnessAuto, contentDescription = null) },
                    label = { Text(stringResource(R.string.theme_system)) },
                )
                SegmentedButton(
                    selected = theme.mode == ThemeMode.LIGHT,
                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
                    label = { Text(stringResource(R.string.theme_light)) },
                )
                SegmentedButton(
                    selected = theme.mode == ThemeMode.DARK,
                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                    label = { Text(stringResource(R.string.theme_dark)) },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.setting_dynamic_color_desc)) },
                    trailingContent = {
                        Switch(
                            checked = theme.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                        )
                    },
                )
            }

            SectionHeader(stringResource(R.string.section_storage))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_downloaded_pdfs)) },
                supportingContent = {
                    Text(
                        if (cacheBytes <= 0) stringResource(R.string.cache_empty)
                        else stringResource(R.string.cache_size, formatSize(cacheBytes)),
                    )
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = cacheBytes > 0,
                    ) {
                        Icon(
                            Icons.Filled.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.action_clear))
                    }
                },
            )

            SectionHeader(stringResource(R.string.section_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_autobackup)) },
                supportingContent = { Text(stringResource(R.string.setting_autobackup_desc)) },
                leadingContent = { Icon(Icons.Filled.Backup, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = backup.enabled,
                        onCheckedChange = { viewModel.setBackupEnabled(it) },
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable { folderPicker.launch(null) },
                headlineContent = { Text(stringResource(R.string.setting_backup_folder)) },
                supportingContent = { Text(backup.folderLabel ?: stringResource(R.string.backup_folder_unset)) },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = backup.treeUri != null) { viewModel.backupNow() },
                headlineContent = { Text(stringResource(R.string.action_backup_now)) },
                supportingContent = { Text(stringResource(R.string.backup_now_desc, backup.destPrefix)) },
                leadingContent = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
            )

            SectionHeader(stringResource(R.string.section_accounts))
            profiles.forEach { profile ->
                val active = profile.id == activeProfileId
                ListItem(
                    modifier = Modifier.clickable { viewModel.switchProfile(profile.id) },
                    leadingContent = {
                        Icon(
                            imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle,
                            contentDescription = if (active) stringResource(R.string.account_active_cd) else null,
                            tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(stringResource(R.string.account_summary, profile.config.bucket, profile.config.region)) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeProfile(profile.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.account_remove_cd))
                        }
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { onAddAccount() },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.action_add_account)) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { confirmSignOut = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.action_sign_out_all))
                }
            }

            if (viewModel.syncEnabled) {
                CloudSyncSection(
                    authState = authState,
                    busy = cloudBusy,
                    error = cloudError,
                    onSignIn = viewModel::signIn,
                    onRegister = viewModel::register,
                    onSyncNow = viewModel::syncNow,
                    // Sign-in is mandatory, so leaving the account is a full sign-out: cloud
                    // session + local S3 config, then back to the sign-in screen.
                    onSignOut = {
                        viewModel.signOut()
                        onSignedOut()
                    },
                )
            }

            SectionHeader(stringResource(R.string.section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_version)) },
                supportingContent = { Text(stringResource(R.string.version_value, viewModel.appVersion)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_licenses)) },
                leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                modifier = Modifier.clickable { showLicenses = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_clear_recents)) },
                supportingContent = { Text(stringResource(R.string.setting_clear_recents_desc)) },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                modifier = Modifier.clickable { confirmClearRecents = true },
            )
        }
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }

    if (confirmClearRecents) {
        AlertDialog(
            onDismissRequest = { confirmClearRecents = false },
            title = { Text(stringResource(R.string.dialog_clear_recents_title)) },
            text = { Text(stringResource(R.string.dialog_clear_recents_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearRecents = false
                    viewModel.clearRecents()
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearRecents = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = { Text(stringResource(R.string.dialog_clear_cache_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearCache()
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.dialog_sign_out_title)) },
            text = { Text(stringResource(R.string.dialog_sign_out_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                    onSignedOut()
                }) { Text(stringResource(R.string.action_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private val OPEN_SOURCE_LIBRARIES = listOf(
    "Jetpack Compose & AndroidX" to "Apache License 2.0",
    "AWS SDK for Kotlin" to "Apache License 2.0",
    "PdfBox-Android (Tom Roush)" to "Apache License 2.0",
    "Telephoto" to "Apache License 2.0",
    "Hilt / Dagger" to "Apache License 2.0",
    "Kotlin Coroutines" to "Apache License 2.0",
)

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_licenses)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OPEN_SOURCE_LIBRARIES.forEach { (name, license) ->
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun CloudSyncSection(
    authState: AuthState,
    busy: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    SectionHeader(stringResource(R.string.section_cloud))
    if (authState.signedIn) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.cloud_signed_in_as, authState.email.orEmpty())) },
            supportingContent = { Text(stringResource(R.string.cloud_desc)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.cloud_sign_out)) }
            Button(onClick = onSyncNow, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.cloud_sync_now))
            }
        }
    } else {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val valid = email.isNotBlank() && password.length >= 8
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.cloud_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.cloud_email)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.cloud_password)) },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onRegister(email, password) },
                    enabled = valid && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.cloud_create_account)) }
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = valid && !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.cloud_sign_in))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

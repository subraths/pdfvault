package com.pdfvault.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfvault.R

private val COMMON_REGIONS = listOf(
    "us-east-1", "us-east-2", "us-west-1", "us-west-2",
    "eu-west-1", "eu-west-2", "eu-central-1",
    "ap-south-1", "ap-southeast-1", "ap-southeast-2", "ap-northeast-1",
    "sa-east-1", "ca-central-1",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onConfigured: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.done) {
        // Hand off to the browser once configuration succeeds.
        androidx.compose.runtime.LaunchedEffect(Unit) { onConfigured() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.setup_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.accessKeyId,
                onValueChange = viewModel::onAccessKeyChange,
                label = { Text(stringResource(R.string.setup_access_key)) },
                singleLine = true,
                enabled = !state.connected,
                modifier = Modifier.fillMaxWidth(),
            )

            SecretField(
                value = state.secretAccessKey,
                onValueChange = viewModel::onSecretChange,
                enabled = !state.connected,
            )

            RegionDropdown(
                region = state.region,
                enabled = !state.connected,
                onRegionChange = viewModel::onRegionChange,
            )

            if (!state.connected) {
                Button(
                    onClick = viewModel::connect,
                    enabled = !state.isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(0.dp))
                        Text(stringResource(R.string.setup_connecting))
                    } else {
                        Text(stringResource(R.string.setup_connect))
                    }
                }
            } else {
                BucketChooser(state = state, viewModel = viewModel)
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.setup_secret)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.setup_hide_secret else R.string.setup_show_secret),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDropdown(region: String, enabled: Boolean, onRegionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = region,
            onValueChange = onRegionChange,
            label = { Text(stringResource(R.string.setup_region)) },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            COMMON_REGIONS.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onRegionChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BucketChooser(state: SetupUiState, viewModel: SetupViewModel) {
    Divider()
    Text(stringResource(R.string.setup_choose_bucket), style = MaterialTheme.typography.titleMedium)

    val buckets = state.buckets.orEmpty()
    if (buckets.isEmpty()) {
        Text(
            stringResource(R.string.setup_no_buckets),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    buckets.forEach { name ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = state.selectedBucket == name, onClick = { viewModel.onSelectBucket(name) })
                .padding(vertical = 4.dp),
        ) {
            RadioButton(selected = state.selectedBucket == name, onClick = { viewModel.onSelectBucket(name) })
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.setup_create_bucket), style = MaterialTheme.typography.titleSmall)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = state.newBucketName,
            onValueChange = viewModel::onNewBucketNameChange,
            label = { Text(stringResource(R.string.setup_new_bucket_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = viewModel::createBucket, enabled = !state.isWorking) {
            Text(stringResource(R.string.action_create))
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = viewModel::confirm,
        enabled = !state.isWorking && state.selectedBucket != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isWorking) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.setup_working))
        } else {
            Text(stringResource(R.string.setup_continue))
        }
    }
}

package com.pdfvault.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.pdfvault.desktop.data.CredentialStore
import com.pdfvault.desktop.model.S3Config
import com.pdfvault.desktop.s3.S3Setup
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(credentials: CredentialStore, onConfigured: (S3Config) -> Unit) {
    val scope = rememberCoroutineScope()
    var accessKey by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var buckets by remember { mutableStateOf<List<String>?>(null) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    var newBucket by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Text("Connect to S3", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter an access key with S3 permissions, then pick a bucket.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it; error = null },
                label = { Text("Access key ID") },
                singleLine = true,
                enabled = buckets == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it; error = null },
                label = { Text("Secret access key") },
                singleLine = true,
                enabled = buckets == null,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = region,
                onValueChange = { region = it; error = null },
                label = { Text("Region") },
                singleLine = true,
                enabled = buckets == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            if (buckets == null) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            runCatching {
                                S3Setup.listBuckets(accessKey.trim(), secret.trim(), region.trim())
                            }.onSuccess { list ->
                                buckets = list
                                selectedBucket = list.firstOrNull()
                            }.onFailure { error = it.message ?: "Couldn't connect." }
                            busy = false
                        }
                    },
                    enabled = !busy && accessKey.isNotBlank() && secret.isNotBlank(),
                ) { Text("Connect") }
            } else {
                Text("Choose a bucket", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                buckets!!.forEach { bucket ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = bucket == selectedBucket, onClick = { selectedBucket = bucket })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = bucket == selectedBucket, onClick = { selectedBucket = bucket })
                        Spacer(Modifier.width(8.dp))
                        Text(bucket)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newBucket,
                        onValueChange = { newBucket = it; error = null },
                        label = { Text("New bucket name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                val name = newBucket.trim()
                                runCatching {
                                    S3Setup.createBucket(name, accessKey.trim(), secret.trim(), region.trim())
                                }.onSuccess {
                                    buckets = (buckets.orEmpty() + name).distinct().sorted()
                                    selectedBucket = name
                                    newBucket = ""
                                }.onFailure { error = it.message ?: "Couldn't create bucket." }
                                busy = false
                            }
                        },
                        enabled = !busy && newBucket.isNotBlank(),
                    ) { Text("Create") }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val bucket = selectedBucket ?: return@Button
                        scope.launch {
                            busy = true; error = null
                            val resolvedRegion = S3Setup.resolveBucketRegion(
                                bucket = bucket,
                                accessKeyId = accessKey.trim(),
                                secretAccessKey = secret.trim(),
                                fallbackRegion = region.trim(),
                            )
                            val config = S3Config(accessKey.trim(), secret.trim(), resolvedRegion, bucket)
                            runCatching { credentials.save(config) }
                                .onSuccess { onConfigured(config) }
                                .onFailure { error = it.message ?: "Couldn't save." }
                            busy = false
                        }
                    },
                    enabled = !busy && selectedBucket != null,
                ) { Text("Use this bucket") }
            }

            Spacer(Modifier.height(16.dp))
            if (busy) CircularProgressIndicator()
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

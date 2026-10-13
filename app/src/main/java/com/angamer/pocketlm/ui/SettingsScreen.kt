package com.angamer.pocketlm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.angamer.pocketlm.AppPrefs
import com.angamer.pocketlm.BuildConfig
import com.angamer.pocketlm.server.ApiKey
import com.angamer.pocketlm.server.ApiKeys
import com.angamer.pocketlm.update.UpdateChecker
import com.angamer.pocketlm.update.UpdateMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var portText by remember { mutableStateOf(AppPrefs.port(context).toString()) }
    var serverOn by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(AppPrefs.temperature(context)) }
    var maxTokens by remember { mutableIntStateOf(AppPrefs.maxTokens(context)) }
    var updateMode by remember { mutableStateOf(AppPrefs.updateMode(context)) }
    var updateStatus by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }

    var keys by remember { mutableStateOf(ApiKeys.list(context)) }
    var keyName by remember { mutableStateOf("") }
    var keyPlan by remember { mutableStateOf("free") }
    var keyRpm by remember { mutableIntStateOf(30) }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun persistKeys(next: List<ApiKey>) {
        keys = next
        ApiKeys.save(context, next)
    }

    fun runCheck(autoInstall: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            updateProgress = 0f
            updateStatus = "Checking…"
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
                if (info.remoteCode <= BuildConfig.VERSION_CODE) {
                    updateStatus = "Up to date · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                } else {
                    updateStatus =
                        "Update available: ${info.remoteName} (${info.remoteCode})\n${info.notes}"
                    if (autoInstall || updateMode == UpdateMode.AUTO) {
                        val url = info.apkUrl
                        if (url.isNullOrBlank()) {
                            updateStatus += "\nNo APK on the latest GitHub release yet."
                        } else {
                            updateStatus += "\nDownloading…"
                            val file = withContext(Dispatchers.IO) {
                                UpdateChecker.download(context, url) { p -> updateProgress = p }
                            }
                            updateStatus += "\nOpening installer (you confirm)."
                            UpdateChecker.install(context, file)
                        }
                    }
                }
            } catch (e: Exception) {
                updateStatus = "Check failed: ${e.message}"
            } finally {
                checking = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall
        )

        Text(text = "LAN server", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Off by default. Chat works without it.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 9090
                    AppPrefs.setPort(context, port)
                    portText = port.toString()
                    onStartServer(port)
                    serverOn = true
                },
                enabled = !serverOn
            ) { Text("Start server") }
            OutlinedButton(
                onClick = {
                    onStopServer()
                    serverOn = false
                },
                enabled = serverOn
            ) { Text("Stop server") }
        }

        Text(text = "Chat", style = MaterialTheme.typography.titleMedium)
        Text(text = "Temperature ${String.format("%.1f", temperature)}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(0.2f, 0.7f, 1.0f, 1.3f)) {
                OutlinedButton(onClick = {
                    temperature = value
                    AppPrefs.setTemperature(context, value)
                }) { Text(String.format("%.1f", value)) }
            }
        }
        Text(text = "Max tokens $maxTokens", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(64, 128, 256, 512)) {
                OutlinedButton(onClick = {
                    maxTokens = value
                    AppPrefs.setMaxTokens(context, value)
                }) { Text(value.toString()) }
            }
        }

        Text(text = "Updates", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Do nothing · check on start · auto (download + system install prompt)",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in UpdateMode.entries) {
                val label = when (mode) {
                    UpdateMode.OFF -> "Off"
                    UpdateMode.CHECK_ON_START -> "On start"
                    UpdateMode.AUTO -> "Auto"
                }
                OutlinedButton(onClick = {
                    updateMode = mode
                    AppPrefs.setUpdateMode(context, mode)
                }) {
                    Text(if (updateMode == mode) "[$label]" else label)
                }
            }
        }
        Button(
            onClick = { runCheck(autoInstall = true) },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Check for updates") }
        if (checking && updateProgress > 0f) {
            LinearProgressIndicator(
                progress = { updateProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (updateStatus.isNotBlank()) {
            Text(text = updateStatus, style = MaterialTheme.typography.bodySmall)
        }

        Text(text = "API keys", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "No keys = server is open. Add any key and /v1 requires Authorization: Bearer <key>.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = keyName,
            onValueChange = { keyName = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "Plan $keyPlan · rpm $keyRpm", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((plan, rpm) in listOf("free" to 30, "plus" to 120, "unlimited" to 0)) {
                OutlinedButton(onClick = {
                    keyPlan = plan
                    keyRpm = rpm
                }) { Text(plan) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (rpm in listOf(10, 30, 60, 120)) {
                OutlinedButton(onClick = {
                    keyRpm = rpm
                    if (keyPlan == "unlimited") keyPlan = "custom"
                }) { Text("$rpm/m") }
            }
        }
        Button(
            onClick = {
                val name = keyName.trim().ifBlank { "key" }
                if (editingId != null) {
                    persistKeys(keys.map {
                        if (it.id == editingId) it.copy(name = name, plan = keyPlan, rpm = keyRpm) else it
                    })
                    editingId = null
                } else {
                    persistKeys(
                        keys + ApiKey(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            secret = ApiKeys.newSecret(),
                            plan = keyPlan,
                            rpm = keyRpm,
                            enabled = true
                        )
                    )
                }
                keyName = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (editingId != null) "Save key" else "Create key")
        }

        for (key in keys) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = key.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${key.plan} · ${if (key.rpm <= 0) "no rpm limit" else "${key.rpm} rpm"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(text = key.secret, style = MaterialTheme.typography.bodySmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Enabled")
                        Switch(
                            checked = key.enabled,
                            onCheckedChange = { on ->
                                persistKeys(keys.map {
                                    if (it.id == key.id) it.copy(enabled = on) else it
                                })
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("api key", key.secret))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                        OutlinedButton(onClick = {
                            editingId = key.id
                            keyName = key.name
                            keyPlan = key.plan
                            keyRpm = key.rpm
                        }) { Text("Edit") }
                        OutlinedButton(onClick = {
                            persistKeys(keys.filterNot { it.id == key.id })
                            if (editingId == key.id) editingId = null
                        }) { Text("Delete") }
                    }
                }
            }
        }

        Text(text = "LiteRT", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Catalog can show LiteRT models, but inference is llama.cpp only.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

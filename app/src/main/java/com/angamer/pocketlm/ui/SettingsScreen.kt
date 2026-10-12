package com.angamer.pocketlm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.angamer.pocketlm.AppPrefs

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current
    var portText by remember { mutableStateOf(AppPrefs.port(context).toString()) }
    var serverOn by remember { mutableStateOf(false) }
    var temperature by remember { mutableFloatStateOf(AppPrefs.temperature(context)) }
    var maxTokens by remember { mutableIntStateOf(AppPrefs.maxTokens(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Text(text = "LAN server", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Off by default. Chat works without it. Turn it on only when another device should call the OpenAI API.",
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
        Text(
            text = if (serverOn) "Server marked on · :$portText" else "Server off",
            style = MaterialTheme.typography.bodySmall
        )

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
            text = "Debug and release APKs are signed with the keystore in the git repo. Uninstall pocketLM once so Android accepts the new signature. After that, new builds install over the old one.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(text = "LiteRT", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Catalog can show LiteRT models, but inference is llama.cpp only. A LiteRT / .litertlm backend is not linked in this build.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

package com.angamer.pocketlm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.angamer.pocketlm.native.LlamaBridge
import com.angamer.pocketlm.server.LlmServerService
import com.angamer.pocketlm.ui.theme.PocketLMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketLMTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onStartServer = { port ->
                            val intent = Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_START
                                putExtra(LlmServerService.EXTRA_PORT, port)
                            }
                            ContextCompat.startForegroundService(this, intent)
                        },
                        onStopServer = {
                            val intent = Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_STOP
                            }
                            startService(intent)
                        },
                        onLoadModel = { path ->
                            val intent = Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_LOAD_MODEL
                                putExtra(LlmServerService.EXTRA_MODEL_PATH, path)
                            }
                            startService(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit,
    onLoadModel: (String) -> Unit
) {
    var port by remember { mutableStateOf("9090") }
    var modelPath by remember { mutableStateOf("/sdcard/Download/model.gguf") }
    var serverRunning by remember { mutableStateOf(false) }
    var nativeStatus by remember { mutableStateOf("checking...") }
    var modelStatus by remember { mutableStateOf("no model") }

    LaunchedEffect(Unit) {
        nativeStatus = try {
            LlamaBridge.nativeHello()
        } catch (e: Exception) {
            "native load failed: ${e.message}"
        }
        modelStatus = if (LlamaBridge.isLoaded()) "model loaded" else "no model"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "pocketLM", style = MaterialTheme.typography.headlineMedium)
        Text(text = "on-device LLM server", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { newValue -> port = newValue.filter { it.isDigit() } },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = modelPath,
            onValueChange = { modelPath = it },
            label = { Text("GGUF path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onLoadModel(modelPath.trim())
                    modelStatus = "loading..."
                }
            ) {
                Text("Load Model")
            }
            OutlinedButton(
                onClick = {
                    LlamaBridge.unloadModel()
                    modelStatus = "no model"
                }
            ) {
                Text("Unload")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val p = port.toIntOrNull() ?: 9090
                    onStartServer(p)
                    serverRunning = true
                },
                enabled = !serverRunning
            ) {
                Text("Start Server")
            }
            OutlinedButton(
                onClick = {
                    onStopServer()
                    serverRunning = false
                },
                enabled = serverRunning
            ) {
                Text("Stop Server")
            }
        }

        Text(
            text = if (serverRunning) "Server :$port" else "Server stopped",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(text = "Model: $modelStatus", style = MaterialTheme.typography.bodyMedium)
        Text(text = nativeStatus, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Put a .gguf on the phone, set path, Load, then Start Server.\ncurl http://PHONE_IP:PORT/v1/chat/completions",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

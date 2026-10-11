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
    onStopServer: () -> Unit
) {
    var port by remember { mutableStateOf("9090") }
    var serverRunning by remember { mutableStateOf(false) }
    var nativeStatus by remember { mutableStateOf("checking...") }

    LaunchedEffect(Unit) {
        nativeStatus = try {
            LlamaBridge.nativeHello()
        } catch (e: Exception) {
            "native load failed: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "pocketLM", style = MaterialTheme.typography.headlineMedium)
        Text(text = "on-device LLM server", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { newValue -> port = newValue.filter { it.isDigit() } },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text("Stop")
            }
        }

        Text(
            text = if (serverRunning) "Server running on :$port" else "Server stopped",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Native status:", style = MaterialTheme.typography.labelMedium)
        Text(text = nativeStatus, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "llama.cpp integration next\nAPI keys + OpenAI endpoints coming",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

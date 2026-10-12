package com.angamer.pocketlm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.angamer.pocketlm.native.LlamaBridge
import com.angamer.pocketlm.server.LlmServerService
import com.angamer.pocketlm.ui.theme.PocketLMTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ensure models dir exists
        File(filesDir, "models").mkdirs()

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

data class ModelEntry(val name: String, val path: String, val sizeMb: Long)

@Composable
fun MainScreen(
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit,
    onLoadModel: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var port by remember { mutableStateOf("9090") }
    var serverRunning by remember { mutableStateOf(false) }
    var nativeStatus by remember { mutableStateOf("checking...") }
    var modelStatus by remember { mutableStateOf("no model") }
    var models by remember { mutableStateOf<List<ModelEntry>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    fun refreshModels() {
        val dir = File(context.filesDir, "models")
        models = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            ?.map { ModelEntry(it.name, it.absolutePath, it.length() / (1024 * 1024)) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    LaunchedEffect(Unit) {
        nativeStatus = try {
            LlamaBridge.nativeHello()
        } catch (e: Exception) {
            "native failed: ${e.message}"
        }
        modelStatus = if (LlamaBridge.isLoaded()) LlmServerService.loadedModelName else "no model"
        refreshModels()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            importProgress = 0f
            try {
                val name = queryDisplayName(context, uri) ?: "model.gguf"
                val safeName = if (name.endsWith(".gguf", true)) name else "$name.gguf"
                val dest = File(File(context.filesDir, "models"), safeName)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val total = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
                        FileOutputStream(dest).use { output ->
                            val buf = ByteArray(1024 * 256)
                            var copied = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0) {
                                    importProgress = (copied.toFloat() / total).coerceIn(0f, 1f)
                                }
                            }
                        }
                    } ?: throw IllegalStateException("cannot open uri")
                }
                refreshModels()
                selectedPath = dest.absolutePath
                Toast.makeText(context, "Imported $safeName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                importing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "pocketLM", style = MaterialTheme.typography.headlineMedium)
        Text(text = "on-device LLM server", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val p = port.toIntOrNull() ?: 9090
                    onStartServer(p)
                    serverRunning = true
                },
                enabled = !serverRunning
            ) { Text("Start Server") }
            OutlinedButton(
                onClick = {
                    onStopServer()
                    serverRunning = false
                },
                enabled = serverRunning
            ) { Text("Stop") }
        }

        Text(
            text = if (serverRunning) "Server :$port · model: $modelStatus" else "Server stopped · $modelStatus",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Models", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { picker.launch(arrayOf("*/*", "application/octet-stream")) },
                enabled = !importing
            ) { Text("Import GGUF") }
            OutlinedButton(onClick = { refreshModels() }) { Text("Refresh") }
            OutlinedButton(
                onClick = {
                    LlamaBridge.unloadModel()
                    LlmServerService.setLoadedModelName("none")
                    modelStatus = "no model"
                    selectedPath = null
                }
            ) { Text("Unload") }
        }

        if (importing) {
            LinearProgressIndicator(
                progress = { importProgress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = "Importing… ${(importProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        if (models.isEmpty()) {
            Text(
                text = "No models yet. Import a .gguf (start small, ~0.5–1B Q4).",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(models, key = { it.path }) { m ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPath = m.path
                                modelStatus = "loading ${m.name}…"
                                onLoadModel(m.path)
                                // optimistic; real status after load finishes is approximate
                                scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    // poll a bit
                                    repeat(30) {
                                        if (LlamaBridge.isLoaded()) {
                                            modelStatus = m.name
                                            return@launch
                                        }
                                        kotlinx.coroutines.delay(500)
                                    }
                                    modelStatus = if (LlamaBridge.isLoaded()) m.name else "load failed"
                                }
                            }
                    ) {
                        Column(Modifier = Modifier.padding(12.dp)) {
                            Text(text = m.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${m.sizeMb} MB" + if (selectedPath == m.path) " · selected" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (models.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(text = nativeStatus, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "API: POST /v1/chat/completions  (stream: true for SSE)",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.angamer.pocketlm.model.CatalogModel
import com.angamer.pocketlm.model.ModelCatalog
import com.angamer.pocketlm.model.ModelDownloader
import com.angamer.pocketlm.native.LlamaBridge
import com.angamer.pocketlm.server.LlmServerService
import com.angamer.pocketlm.ui.theme.PocketLMTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        File(filesDir, "models").mkdirs()

        setContent {
            PocketLMTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onStartServer = { p ->
                            val intent = Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_START
                                putExtra(LlmServerService.EXTRA_PORT, p)
                            }
                            ContextCompat.startForegroundService(this, intent)
                        },
                        onStopServer = {
                            startService(
                                Intent(this, LlmServerService::class.java).apply {
                                    action = LlmServerService.ACTION_STOP
                                }
                            )
                        },
                        onLoadModel = { path ->
                            startService(
                                Intent(this, LlmServerService::class.java).apply {
                                    action = LlmServerService.ACTION_LOAD_MODEL
                                    putExtra(LlmServerService.EXTRA_MODEL_PATH, path)
                                }
                            )
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
    val modelsDir = remember { File(context.filesDir, "models") }

    var port by remember { mutableStateOf("9090") }
    var serverRunning by remember { mutableStateOf(false) }
    var nativeStatus by remember { mutableStateOf("checking...") }
    var modelStatus by remember { mutableStateOf("no model") }
    var models by remember { mutableStateOf<List<ModelEntry>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    var customUrl by remember { mutableStateOf("") }
    var showCatalog by remember { mutableStateOf(true) }

    fun refreshModels() {
        models = modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            ?.map { ModelEntry(it.name, it.absolutePath, it.length() / (1024 * 1024)) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun isDownloaded(filename: String): Boolean =
        File(modelsDir, filename).exists()

    fun loadPath(path: String, name: String) {
        selectedPath = path
        modelStatus = "loading $name…"
        onLoadModel(path)
        scope.launch {
            repeat(60) {
                delay(500)
                if (LlamaBridge.isLoaded()) {
                    modelStatus = name
                    return@launch
                }
            }
            modelStatus = if (LlamaBridge.isLoaded()) name else "load failed / still loading"
        }
    }

    fun downloadUrl(url: String, filename: String) {
        if (busy) return
        scope.launch {
            busy = true
            busyLabel = "Downloading $filename"
            progress = 0f
            try {
                val dest = File(modelsDir, filename)
                withContext(Dispatchers.IO) {
                    ModelDownloader.download(url, dest) { p ->
                        if (p >= 0f) progress = p
                    }
                }
                refreshModels()
                Toast.makeText(context, "Downloaded $filename", Toast.LENGTH_SHORT).show()
                loadPath(dest.absolutePath, filename)
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    fun downloadCatalog(m: CatalogModel) {
        if (isDownloaded(m.filename)) {
            loadPath(File(modelsDir, m.filename).absolutePath, m.filename)
            return
        }
        downloadUrl(m.url, m.filename)
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
            busy = true
            busyLabel = "Importing…"
            progress = 0f
            try {
                val name = queryDisplayName(context, uri) ?: "model.gguf"
                val safeName = if (name.endsWith(".gguf", true)) name else "$name.gguf"
                val dest = File(modelsDir, safeName)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val total = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
                        FileOutputStream(dest).use { output ->
                            val buf = ByteArray(256 * 1024)
                            var copied = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0) progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    } ?: error("cannot open file")
                }
                refreshModels()
                Toast.makeText(context, "Imported $safeName", Toast.LENGTH_SHORT).show()
                loadPath(dest.absolutePath, safeName)
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                    onStartServer(port.toIntOrNull() ?: 9090)
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
            text = if (serverRunning) "Server :$port · $modelStatus" else "Stopped · $modelStatus",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(text = "Download online", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCatalog = !showCatalog }) {
                Text(if (showCatalog) "Hide catalog" else "Show catalog")
            }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*", "application/octet-stream")) },
                enabled = !busy
            ) { Text("Import file") }
        }

        if (showCatalog) {
            for (m in ModelCatalog.models) {
                CatalogCard(
                    model = m,
                    downloaded = isDownloaded(m.filename),
                    enabled = !busy,
                    onClick = { downloadCatalog(m) }
                )
            }
        }

        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it },
            label = { Text("Custom GGUF URL (HF resolve link)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        )

        Button(
            onClick = {
                val url = customUrl.trim()
                if (url.isEmpty()) {
                    Toast.makeText(context, "paste a URL first", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val filename = url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "model.gguf" }
                    .let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
                downloadUrl(url, filename)
            },
            enabled = !busy && customUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download URL") }

        if (busy) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "$busyLabel · ${(progress * 100).toInt().coerceIn(0, 100)}%",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(text = "Installed models", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (models.isEmpty()) {
            Text(
                text = "None yet — grab one from the catalog above.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            for (m in models) {
                InstalledCard(
                    entry = m,
                    selected = selectedPath == m.path,
                    enabled = !busy,
                    onClick = { loadPath(m.path, m.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = nativeStatus, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "API: POST /v1/chat/completions  (\"stream\": true for SSE)",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CatalogCard(
    model: CatalogModel,
    downloaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = model.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    append(model.sizeLabel)
                    if (model.note.isNotBlank()) append(" · ").append(model.note)
                    if (downloaded) append(" · downloaded")
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = if (downloaded) "tap to load" else "tap to download + load",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun InstalledCard(
    entry: ModelEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = entry.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${entry.sizeMb} MB" + if (selected) " · selected" else "",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return uri.lastPathSegment
}

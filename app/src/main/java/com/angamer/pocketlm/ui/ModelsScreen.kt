package com.angamer.pocketlm.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.angamer.pocketlm.AppPrefs
import com.angamer.pocketlm.model.CatalogModel
import com.angamer.pocketlm.model.ModelCatalog
import com.angamer.pocketlm.model.ModelDownloader
import com.angamer.pocketlm.model.formatBytes
import com.angamer.pocketlm.model.tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private data class Installed(val name: String, val path: String, val bytes: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsScreen(
    modifier: Modifier = Modifier,
    loadedName: String,
    onLoad: (String) -> Unit,
    onUnload: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelsDir = remember { File(context.filesDir, "models").apply { mkdirs() } }

    var installed by remember { mutableStateOf<List<Installed>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var customUrl by remember { mutableStateOf("") }
    var nCtx by remember { mutableIntStateOf(AppPrefs.nCtx(context)) }
    var threads by remember { mutableIntStateOf(AppPrefs.threads(context)) }

    fun refresh() {
        installed = modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", true) }
            ?.map { Installed(it.name, it.absolutePath, it.length()) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    fun have(name: String) = File(modelsDir, name).exists()

    fun download(url: String, filename: String, thenLoad: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            busyLabel = "Downloading $filename"
            progress = 0f
            try {
                val dest = File(modelsDir, filename)
                withContext(Dispatchers.IO) {
                    ModelDownloader.download(url, dest) { p -> if (p >= 0f) progress = p }
                }
                refresh()
                Toast.makeText(context, "Saved $filename", Toast.LENGTH_SHORT).show()
                if (thenLoad) onLoad(dest.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    fun useCatalog(model: CatalogModel) {
        if (!model.downloadable) {
            Toast.makeText(context, model.note, Toast.LENGTH_LONG).show()
            return
        }
        val file = File(modelsDir, model.filename)
        if (file.exists()) onLoad(file.absolutePath) else download(model.url, model.filename, true)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            busyLabel = "Importing"
            progress = 0f
            try {
                val name = queryName(context, uri) ?: "model.gguf"
                val safe = if (name.endsWith(".gguf", true)) name else "$name.gguf"
                val dest = File(modelsDir, safe)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val total = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
                        FileOutputStream(dest).use { out ->
                            val buf = ByteArray(256 * 1024)
                            var copied = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                out.write(buf, 0, n)
                                copied += n
                                if (total > 0) progress = copied.toFloat() / total
                            }
                        }
                    } ?: error("cannot open file")
                }
                refresh()
                onLoad(dest.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "import failed", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "Models", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (loadedName.isNotBlank() && loadedName != "none") "Loaded: $loadedName" else "Nothing loaded",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(text = "Load settings", style = MaterialTheme.typography.titleMedium)
        Text(text = "Context $nCtx · applied next time you load", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(512, 1024, 2048, 4096)) {
                OutlinedButton(onClick = {
                    nCtx = value
                    AppPrefs.setNCtx(context, value)
                }) { Text(value.toString()) }
            }
        }
        Text(text = "Threads $threads", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(2, 4, 6, 8)) {
                OutlinedButton(onClick = {
                    threads = value
                    AppPrefs.setThreads(context, value)
                }) { Text(value.toString()) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Import") }
            OutlinedButton(onClick = onUnload) { Text("Unload") }
        }

        if (busy) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(text = "$busyLabel · ${(progress * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.bodySmall)
        }

        Text(text = "Download", style = MaterialTheme.typography.titleMedium)
        for (model in ModelCatalog.models) {
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { useCatalog(model) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = model.name, style = MaterialTheme.typography.titleMedium)
                    if (model.note.isNotBlank()) {
                        Text(text = model.note, style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in model.tags()) {
                            Text(
                                text = tag,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(
                        text = when {
                            !model.downloadable -> "LiteRT — not runnable yet"
                            have(model.filename) -> "Downloaded · tap to load"
                            else -> "Tap to download"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom GGUF URL") },
            singleLine = true,
            enabled = !busy
        )
        Button(
            onClick = {
                val url = customUrl.trim()
                if (url.isEmpty()) return@Button
                val filename = url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "model.gguf" }
                    .let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
                download(url, filename, true)
            },
            enabled = !busy && customUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download URL") }

        Text(text = "On device", style = MaterialTheme.typography.titleMedium)
        if (installed.isEmpty()) {
            Text(text = "No GGUF files yet.", style = MaterialTheme.typography.bodyMedium)
        }
        for (file in installed) {
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onLoad(file.path) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatBytes(file.bytes) + if (file.name == loadedName) " · loaded" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return uri.lastPathSegment
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.angamer.pocketlm.model.ModelDownloads
import com.angamer.pocketlm.model.formatBytes
import com.angamer.pocketlm.model.tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val jobs by ModelDownloads.jobs.collectAsState()

    var installed by remember { mutableStateOf<List<Installed>>(emptyList()) }
    var customUrl by remember { mutableStateOf("") }
    var nCtx by remember { mutableIntStateOf(AppPrefs.nCtx(context)) }
    var threads by remember { mutableIntStateOf(AppPrefs.threads(context)) }
    var gpuLayers by remember { mutableIntStateOf(AppPrefs.gpuLayers(context)) }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }

    fun refresh() {
        installed = ModelDownloads.allModels(context)
            .map { Installed(it.name, it.absolutePath, it.length()) }
    }

    LaunchedEffect(Unit) {
        ModelDownloads.ensureReceiver(context)
        refresh()
    }

    LaunchedEffect(jobs.any { it.running }) {
        while (jobs.any { it.running } || ModelDownloads.anyRunning()) {
            ModelDownloads.poll(context)
            delay(500)
        }
    }

    LaunchedEffect(jobs) {
        for (job in jobs) {
            if (!job.running && job.finishedPath != null && job.error == null) {
                refresh()
                Toast.makeText(context, "Saved ${job.filename}", Toast.LENGTH_SHORT).show()
                if (job.thenLoad) onLoad(job.finishedPath)
                ModelDownloads.clearFinished(job.id)
            } else if (!job.running && job.error != null) {
                if (job.error != "cancelled") {
                    Toast.makeText(context, job.error, Toast.LENGTH_LONG).show()
                }
                ModelDownloads.clearFinished(job.id)
            }
        }
    }

    fun have(name: String) = ModelDownloads.allModels(context).any { it.name == name }

    fun download(url: String, filename: String, thenLoad: Boolean) {
        if (ModelDownloads.isActive(filename)) {
            Toast.makeText(context, "Already downloading $filename", Toast.LENGTH_SHORT).show()
            return
        }
        ModelDownloads.enqueue(context, url, filename, thenLoad)
        Toast.makeText(context, "Downloading $filename…", Toast.LENGTH_SHORT).show()
    }

    fun useCatalog(model: CatalogModel) {
        if (!model.downloadable) {
            Toast.makeText(context, model.note, Toast.LENGTH_LONG).show()
            return
        }
        val existing = ModelDownloads.allModels(context).firstOrNull { it.name == model.filename }
        if (existing != null) onLoad(existing.absolutePath)
        else download(model.url, model.filename, true)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            importProgress = 0f
            try {
                val name = queryName(context, uri) ?: "model.gguf"
                val dest = File(ModelDownloads.modelsDir(context), name)
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
                                if (total > 0) importProgress = copied.toFloat() / total
                            }
                        }
                    } ?: error("cannot open file")
                }
                refresh()
                onLoad(dest.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "import failed", Toast.LENGTH_LONG).show()
            } finally {
                importing = false
            }
        }
    }

    val activeJobs = jobs.filter { it.running }

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
        Text(text = "Context $nCtx · next load", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(512, 1024, 2048, 4096)) {
                OutlinedButton(onClick = {
                    nCtx = value
                    AppPrefs.setNCtx(context, value)
                }) { Text(value.toString()) }
            }
        }
        Text(text = "Threads $threads (auto on first run)", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(2, 4, 6, 8)) {
                OutlinedButton(onClick = {
                    threads = value
                    AppPrefs.setThreads(context, value)
                }) { Text(value.toString()) }
            }
        }
        Text(
            text = "GPU layers $gpuLayers (0 = CPU only; needs GPU backend later for real offload)",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf(0, 8, 16, 32, 99)) {
                OutlinedButton(onClick = {
                    gpuLayers = value
                    AppPrefs.setGpuLayers(context, value)
                }) { Text(if (value == 99) "all" else value.toString()) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing) {
                Text("Import")
            }
            OutlinedButton(onClick = onUnload) { Text("Unload") }
        }

        if (importing) {
            LinearProgressIndicator(
                progress = { importProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = "Importing…", style = MaterialTheme.typography.bodySmall)
        }

        if (activeJobs.isNotEmpty()) {
            Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
            for (job in activeJobs) {
                LinearProgressIndicator(
                    progress = { job.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${job.filename} · ${(job.progress * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { ModelDownloads.cancel(context, job.id) }) {
                        Text("Cancel")
                    }
                }
            }
            if (activeJobs.size > 1) {
                OutlinedButton(onClick = { ModelDownloads.cancelAll(context) }) {
                    Text("Cancel all")
                }
            }
        }

        Text(text = "Download", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "System DownloadManager · queue several · leave the tab freely",
            style = MaterialTheme.typography.bodySmall
        )
        for (model in ModelCatalog.models) {
            val downloading = model.filename.isNotBlank() && ModelDownloads.isActive(model.filename)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !downloading) { useCatalog(model) }
            ) {
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
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(
                        text = when {
                            !model.downloadable -> "Import a MediaPipe/LiteRT file"
                            downloading -> "Downloading…"
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
            label = { Text("Custom model URL") },
            singleLine = true
        )
        Button(
            onClick = {
                val url = customUrl.trim()
                if (url.isEmpty()) return@Button
                val filename = url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "model.gguf" }
                download(url, filename, true)
            },
            enabled = customUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download URL") }

        Text(text = "On device", style = MaterialTheme.typography.titleMedium)
        if (installed.isEmpty()) {
            Text(text = "No model files yet.", style = MaterialTheme.typography.bodyMedium)
        }
        for (file in installed) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLoad(file.path) }
            ) {
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

package com.angamer.pocketlm.model

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class DownloadJob(
    val id: Long,
    val filename: String,
    val thenLoad: Boolean,
    val progress: Float,
    val running: Boolean,
    val error: String? = null,
    val finishedPath: String? = null
)

/**
 * Process-wide download state so switching tabs does not reset progress.
 * Uses system [DownloadManager] (no in-app confirm dialog).
 */
object ModelDownloads {

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private var receiverRegistered = false

    fun modelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun legacyModelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun allGguf(context: Context): List<File> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<File>()
        for (dir in listOf(modelsDir(context), legacyModelsDir(context))) {
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".gguf", true) }
                ?.forEach { f ->
                    if (seen.add(f.name)) out += f
                }
        }
        return out.sortedBy { it.name }
    }

    fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        val app = context.applicationContext
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(completeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(completeReceiver, filter)
        }
        receiverRegistered = true
    }

    fun enqueue(context: Context, url: String, filename: String, thenLoad: Boolean): Long {
        ensureReceiver(context)
        val app = context.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // drop finished jobs for same file; allow only one active per filename
        _jobs.update { list ->
            list.filterNot { it.filename == filename && !it.running }
        }
        if (_jobs.value.any { it.filename == filename && it.running }) {
            return _jobs.value.first { it.filename == filename && it.running }.id
        }

        modelsDir(app).mkdirs()
        val dest = File(modelsDir(app), filename)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("pocketLM model")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, null, "models/$filename")

        val id = dm.enqueue(request)
        _jobs.update { it + DownloadJob(id, filename, thenLoad, 0f, true) }
        return id
    }

    fun isActive(filename: String): Boolean =
        _jobs.value.any { it.filename == filename && it.running }

    fun anyRunning(): Boolean = _jobs.value.any { it.running }

    fun poll(context: Context) {
        val app = context.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val current = _jobs.value
        if (current.none { it.running }) return

        _jobs.update { list ->
            list.map { job ->
                if (!job.running) return@map job
                val q = DownloadManager.Query().setFilterById(job.id)
                dm.query(q)?.use { c ->
                    if (!c.moveToFirst()) return@map job
                    val status = c.int(DownloadManager.COLUMN_STATUS)
                    val soFar = c.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val total = c.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val progress = if (total > 0) (soFar.toFloat() / total).coerceIn(0f, 1f) else job.progress
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val path = File(modelsDir(app), job.filename).absolutePath
                            job.copy(running = false, progress = 1f, finishedPath = path, error = null)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.int(DownloadManager.COLUMN_REASON)
                            job.copy(running = false, error = "download failed ($reason)")
                        }
                        else -> job.copy(progress = progress)
                    }
                } ?: job
            }
        }
    }

    fun consumeFinished(): List<DownloadJob> {
        val done = _jobs.value.filter { !it.running && it.finishedPath != null && it.error == null }
        // keep them visible briefly; caller may clear
        return done
    }

    fun clearFinished(id: Long) {
        _jobs.update { it.filterNot { j -> j.id == id && !j.running } }
    }

    private val completeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            poll(context)
        }
    }

    private fun Cursor.int(col: String): Int {
        val i = getColumnIndex(col)
        return if (i >= 0) getInt(i) else -1
    }

    private fun Cursor.long(col: String): Long {
        val i = getColumnIndex(col)
        return if (i >= 0) getLong(i) else -1L
    }
}

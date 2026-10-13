package com.angamer.pocketlm.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val remoteCode: Int,
    val remoteName: String,
    val apkUrl: String?,
    val notes: String
)

object UpdateChecker {
    private const val VERSION_URL =
        "https://raw.githubusercontent.com/angamer234k/pocketLM/main/latest.json"
    private const val RELEASE_URL =
        "https://api.github.com/repos/angamer234k/pocketLM/releases/tags/latest"

    @Volatile
    var last: UpdateInfo? = null

    fun check(): UpdateInfo {
        // Prefer version.json on the GitHub release (written by CI each build),
        // fall back to latest.json on main. Cache-bust raw CDN.
        var code = 0
        var name = ""
        var notes = ""
        var apk: String? = null

        try {
            val releaseBody = get(RELEASE_URL)
            val release = JSONObject(releaseBody)
            val assets = release.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name")
                    val url = asset.optString("browser_download_url").ifBlank { null }
                    when {
                        assetName == "version.json" && url != null -> {
                            val v = JSONObject(get(url))
                            code = v.optInt("versionCode", code)
                            name = v.optString("versionName", name)
                            notes = v.optString("notes", notes)
                        }
                        assetName.endsWith(".apk") && apk == null -> apk = url
                    }
                }
            }
        } catch (_: Exception) {
            // release missing or unreachable
        }

        if (code <= 0) {
            val version = get("$VERSION_URL?t=${System.currentTimeMillis()}")
            val json = JSONObject(version)
            code = json.optInt("versionCode", 0)
            name = json.optString("versionName", name)
            notes = json.optString("notes", notes)
        }

        if (apk == null) apk = findApk()

        val info = UpdateInfo(code, name, apk, notes)
        last = info
        return info
    }

    fun download(context: Context, url: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "pocketlm-update.apk")
        val tmp = File(dir, "pocketlm-update.apk.part")
        val conn = open(url)
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(tmp).use { output ->
                val buf = ByteArray(256 * 1024)
                var copied = 0L
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    output.write(buf, 0, n)
                    copied += n
                    if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        onProgress(1f)
        return dest
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun findApk(): String? {
        return try {
            val body = get(RELEASE_URL)
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    return asset.optString("browser_download_url").ifBlank { null }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun get(url: String): String {
        val conn = open(url)
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        conn.disconnect()
        if (code !in 200..299) error("HTTP $code")
        return text
    }

    private fun open(start: String): HttpURLConnection {
        var current = start
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "pocketLM")
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("redirect")
                conn.disconnect()
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
            } else {
                return conn
            }
        }
        error("too many redirects")
    }
}

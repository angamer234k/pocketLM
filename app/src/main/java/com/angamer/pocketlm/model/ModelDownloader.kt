package com.angamer.pocketlm.model

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    /**
     * Download [url] to [dest]. Calls [onProgress] with 0f..1f (or -1 if unknown length).
     * Supports basic redirects. Overwrites existing file.
     */
    fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit = {}
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.absolutePath + ".part")

        var currentUrl = url
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "pocketLM/0.3")
                // HF sometimes wants this for large files
                setRequestProperty("Accept", "*/*")
            }

            when (val code = conn.responseCode) {
                in 300..399 -> {
                    val loc = conn.getHeaderField("Location")
                        ?: error("redirect without Location ($code)")
                    currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                    conn.disconnect()
                    redirects++
                    continue
                }
                HttpURLConnection.HTTP_OK -> {
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf = ByteArray(256 * 1024)
                            var copied = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0) {
                                    onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                                } else {
                                    onProgress(-1f)
                                }
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
                    return
                }
                else -> {
                    val msg = conn.errorStream?.bufferedReader()?.readText()?.take(200)
                    conn.disconnect()
                    error("HTTP $code ${msg ?: ""}")
                }
            }
        }
        error("too many redirects")
    }
}

package com.angamer.pocketlm.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.angamer.pocketlm.native.LlamaBridge
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class LlmServerService : Service() {

    companion object {
        const val ACTION_START = "com.angamer.pocketlm.START_SERVER"
        const val ACTION_STOP = "com.angamer.pocketlm.STOP_SERVER"
        const val ACTION_LOAD_MODEL = "com.angamer.pocketlm.LOAD_MODEL"
        const val EXTRA_PORT = "port"
        const val EXTRA_MODEL_PATH = "model_path"
        private const val CHANNEL_ID = "pocketlm_server"
        private const val NOTIF_ID = 1
        private const val TAG = "LlmServerService"
    }

    private var server: PocketHttpServer? = null
    private var currentPort: Int = 9090

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 9090)
                currentPort = port
                startForeground(NOTIF_ID, buildNotification(port))
                startServer(port)
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_LOAD_MODEL -> {
                val path = intent.getStringExtra(EXTRA_MODEL_PATH)
                if (!path.isNullOrBlank()) {
                    Thread {
                        val ok = LlamaBridge.loadModel(path)
                        Log.i(TAG, "loadModel($path) => $ok")
                    }.start()
                }
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        stopServer()
        try {
            server = PocketHttpServer(port).also { it.start() }
            Log.i(TAG, "Server started on port $port")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        Log.i(TAG, "Server stopped")
    }

    private fun buildNotification(port: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LLM Server",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val modelStatus = if (LlamaBridge.isLoaded()) "model loaded" else "no model"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("pocketLM")
            .setContentText(":$port · $modelStatus")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopServer()
        LlamaBridge.unloadModel()
        super.onDestroy()
    }

    private class PocketHttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            return when {
                uri == "/v1/models" && method == Method.GET -> {
                    val id = if (LlamaBridge.isLoaded()) "pocketlm-local" else "none"
                    val body = JSONObject()
                        .put("object", "list")
                        .put("data", JSONArray().put(
                            JSONObject()
                                .put("id", id)
                                .put("object", "model")
                                .put("created", 0)
                                .put("owned_by", "pocketlm")
                        ))
                        .toString()
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                }

                uri == "/v1/chat/completions" && method == Method.POST -> {
                    handleChatCompletions(session)
                }

                uri == "/health" || uri == "/" -> {
                    val status = if (LlamaBridge.isLoaded()) "ok model=loaded" else "ok model=none"
                    newFixedLengthResponse(Response.Status.OK, "text/plain", "pocketLM $status")
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
                }
            }
        }

        private fun handleChatCompletions(session: IHTTPSession): Response {
            if (!LlamaBridge.isLoaded()) {
                val err = JSONObject()
                    .put("error", JSONObject()
                        .put("message", "No model loaded. Use the app to load a GGUF first.")
                        .put("type", "model_not_loaded"))
                    .toString()
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", err)
            }

            val files = HashMap<String, String>()
            return try {
                session.parseBody(files)
                val bodyStr = files["postData"] ?: "{}"
                val json = JSONObject(bodyStr)

                val messages = json.optJSONArray("messages") ?: JSONArray()
                val maxTokens = json.optInt("max_tokens", 256)
                val temperature = json.optDouble("temperature", 0.7).toFloat()

                // simple prompt build: concatenate roles
                val prompt = buildString {
                    for (i in 0 until messages.length()) {
                        val m = messages.getJSONObject(i)
                        val role = m.optString("role", "user")
                        val content = m.optString("content", "")
                        append(role).append(": ").append(content).append("\n")
                    }
                    append("assistant: ")
                }

                val completion = LlamaBridge.complete(prompt, maxTokens, temperature)

                val response = JSONObject()
                    .put("id", "chatcmpl-pocketlm")
                    .put("object", "chat.completion")
                    .put("created", System.currentTimeMillis() / 1000)
                    .put("model", "pocketlm-local")
                    .put("choices", JSONArray().put(
                        JSONObject()
                            .put("index", 0)
                            .put("message", JSONObject()
                                .put("role", "assistant")
                                .put("content", completion))
                            .put("finish_reason", "stop")
                    ))
                    .put("usage", JSONObject()
                        .put("prompt_tokens", 0)
                        .put("completion_tokens", 0)
                        .put("total_tokens", 0))
                    .toString()

                newFixedLengthResponse(Response.Status.OK, "application/json", response)
            } catch (e: Exception) {
                Log.e(TAG, "chat completions error", e)
                val err = JSONObject()
                    .put("error", JSONObject()
                        .put("message", e.message ?: "internal error")
                        .put("type", "server_error"))
                    .toString()
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", err)
            }
        }
    }
}

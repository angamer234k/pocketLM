package com.angamer.pocketlm.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.angamer.pocketlm.native.LlamaBridge
import com.angamer.pocketlm.native.TokenCallback
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors

class LlmServerService : Service() {

    companion object {
        const val ACTION_START = "com.angamer.pocketlm.START_SERVER"
        const val ACTION_STOP = "com.angamer.pocketlm.STOP_SERVER"
        const val ACTION_LOAD_MODEL = "com.angamer.pocketlm.LOAD_MODEL"
        const val EXTRA_PORT = "port"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_N_CTX = "n_ctx"
        const val EXTRA_N_THREADS = "n_threads"
        private const val CHANNEL_ID = "pocketlm_server"
        private const val NOTIF_ID = 1
        private const val TAG = "LlmServerService"

        @Volatile
        var loadedModelName: String = "none"
            private set

        fun setLoadedModelName(name: String) {
            loadedModelName = name
        }
    }

    private var server: PocketHttpServer? = null
    private val inferPool = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 9090)
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
                val nCtx = intent.getIntExtra(EXTRA_N_CTX, 2048)
                val nThreads = intent.getIntExtra(EXTRA_N_THREADS, 4)
                if (!path.isNullOrBlank()) {
                    inferPool.execute {
                        val ok = LlamaBridge.loadModel(path, nCtx, nThreads)
                        val name = path.substringAfterLast('/')
                        if (ok) setLoadedModelName(name) else setLoadedModelName("none")
                        Log.i(TAG, "loadModel($path) ctx=$nCtx threads=$nThreads => $ok")
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        stopServer()
        try {
            server = PocketHttpServer(port, inferPool, applicationContext).also { it.start() }
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

        val modelStatus = if (LlamaBridge.isLoaded()) loadedModelName else "no model"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("pocketLM")
            .setContentText(":$port · $modelStatus")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopServer()
        inferPool.shutdownNow()
        super.onDestroy()
    }

    private class PocketHttpServer(
        port: Int,
        private val inferPool: java.util.concurrent.ExecutorService,
        private val appContext: Context
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.substringBefore('?')
            val method = session.method

            if (method == Method.OPTIONS) {
                return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
            }

            if (uri.startsWith("/v1/")) {
                val authHeader = session.headers["authorization"]
                when (val reason = ApiKeys.check(appContext, authHeader)) {
                    null -> {}
                    else -> {
                        val status = when {
                            reason.startsWith("rate") -> Response.Status.TOO_MANY_REQUESTS
                            reason.startsWith("disabled") -> Response.Status.FORBIDDEN
                            else -> Response.Status.UNAUTHORIZED
                        }
                        val body = JSONObject()
                            .put("error", JSONObject()
                                .put("message", reason)
                                .put("type", "auth_error"))
                            .toString()
                        return cors(newFixedLengthResponse(status, "application/json", body))
                    }
                }
            }

            return when {
                uri == "/v1/models" && method == Method.GET -> cors(handleModels())
                uri == "/v1/chat/completions" && method == Method.POST -> cors(handleChat(session))
                uri == "/health" || uri == "/" -> {
                    val status = if (LlamaBridge.isLoaded()) "ok model=$loadedModelName" else "ok model=none"
                    cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "pocketLM $status"))
                }
                else -> cors(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found"))
            }
        }

        private fun cors(r: Response): Response {
            r.addHeader("Access-Control-Allow-Origin", "*")
            r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            return r
        }

        private fun handleModels(): Response {
            val id = if (LlamaBridge.isLoaded()) loadedModelName else "none"
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
            return newFixedLengthResponse(Response.Status.OK, "application/json", body)
        }

        private fun handleChat(session: IHTTPSession): Response {
            if (!LlamaBridge.isLoaded()) {
                val err = JSONObject()
                    .put("error", JSONObject()
                        .put("message", "No model loaded")
                        .put("type", "model_not_loaded"))
                    .toString()
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", err)
            }

            return try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val bodyStr = files["postData"] ?: "{}"
                val json = JSONObject(bodyStr)

                val messages = json.optJSONArray("messages") ?: JSONArray()
                val maxTokens = json.optInt("max_tokens", 256).coerceIn(1, 4096)
                val temperature = json.optDouble("temperature", 0.7).toFloat().coerceIn(0f, 2f)
                val stream = json.optBoolean("stream", false)
                val prompt = buildPrompt(messages)

                if (stream) {
                    streamResponse(prompt, maxTokens, temperature)
                } else {
                    val completion = LlamaBridge.complete(prompt, maxTokens, temperature)
                    newFixedLengthResponse(Response.Status.OK, "application/json", buildCompletionJson(completion))
                }
            } catch (e: Exception) {
                Log.e(TAG, "chat error", e)
                val err = JSONObject()
                    .put("error", JSONObject()
                        .put("message", e.message ?: "internal error")
                        .put("type", "server_error"))
                    .toString()
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", err)
            }
        }

        private fun buildPrompt(messages: JSONArray): String = buildString {
            for (i in 0 until messages.length()) {
                val m = messages.getJSONObject(i)
                val role = m.optString("role", "user")
                val content = when (val c = m.opt("content")) {
                    is String -> c
                    is JSONArray -> buildString {
                        for (j in 0 until c.length()) {
                            val part = c.optJSONObject(j) ?: continue
                            if (part.optString("type") == "text") append(part.optString("text"))
                        }
                    }
                    else -> ""
                }
                append(role).append(": ").append(content).append('\n')
            }
            append("assistant: ")
        }

        private fun buildCompletionJson(content: String): String {
            return JSONObject()
                .put("id", "chatcmpl-pocketlm")
                .put("object", "chat.completion")
                .put("created", System.currentTimeMillis() / 1000)
                .put("model", loadedModelName)
                .put("choices", JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put("message", JSONObject()
                            .put("role", "assistant")
                            .put("content", content))
                        .put("finish_reason", "stop")
                ))
                .put("usage", JSONObject()
                    .put("prompt_tokens", 0)
                    .put("completion_tokens", 0)
                    .put("total_tokens", 0))
                .toString()
        }

        private fun streamResponse(prompt: String, maxTokens: Int, temperature: Float): Response {
            val pin = PipedInputStream(64 * 1024)
            val pout = PipedOutputStream(pin)

            inferPool.execute {
                try {
                    LlamaBridge.completeStream(prompt, maxTokens, temperature, object : TokenCallback {
                        override fun onToken(token: String) {
                            val chunk = JSONObject()
                                .put("id", "chatcmpl-pocketlm")
                                .put("object", "chat.completion.chunk")
                                .put("created", System.currentTimeMillis() / 1000)
                                .put("model", loadedModelName)
                                .put("choices", JSONArray().put(
                                    JSONObject()
                                        .put("index", 0)
                                        .put("delta", JSONObject().put("content", token))
                                        .put("finish_reason", JSONObject.NULL)
                                ))
                            pout.write("data: $chunk\n\n".toByteArray(Charsets.UTF_8))
                            pout.flush()
                        }

                        override fun onDone() {
                            try {
                                val done = JSONObject()
                                    .put("id", "chatcmpl-pocketlm")
                                    .put("object", "chat.completion.chunk")
                                    .put("created", System.currentTimeMillis() / 1000)
                                    .put("model", loadedModelName)
                                    .put("choices", JSONArray().put(
                                        JSONObject()
                                            .put("index", 0)
                                            .put("delta", JSONObject())
                                            .put("finish_reason", "stop")
                                    ))
                                pout.write("data: $done\n\n".toByteArray(Charsets.UTF_8))
                                pout.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                                pout.flush()
                            } finally {
                                pout.close()
                            }
                        }

                        override fun onError(message: String) {
                            try {
                                pout.write("data: ${JSONObject().put("error", message)}\n\n".toByteArray(Charsets.UTF_8))
                                pout.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                                pout.flush()
                            } finally {
                                try { pout.close() } catch (_: Exception) {}
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "stream failed", e)
                    try { pout.close() } catch (_: Exception) {}
                }
            }

            val response = newChunkedResponse(Response.Status.OK, "text/event-stream", pin)
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader("Connection", "keep-alive")
            response.addHeader("X-Accel-Buffering", "no")
            return response
        }
    }
}

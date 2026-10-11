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
import com.angamer.pocketlm.R
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LlmServerService : Service() {

    companion object {
        const val ACTION_START = "com.angamer.pocketlm.START_SERVER"
        const val ACTION_STOP = "com.angamer.pocketlm.STOP_SERVER"
        const val EXTRA_PORT = "port"
        private const val CHANNEL_ID = "pocketlm_server"
        private const val NOTIF_ID = 1
        private const val TAG = "LlmServerService"
    }

    private var server: PocketHttpServer? = null

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("pocketLM")
            .setContentText("Serving on port $port")
            .setSmallIcon(android.R.drawable.ic_menu_manage) // placeholder
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    // Minimal OpenAI-compatible stub
    private class PocketHttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            return when {
                uri == "/v1/models" && method == Method.GET -> {
                    val body = """
                    {
                      "object": "list",
                      "data": [
                        {
                          "id": "pocketlm-stub",
                          "object": "model",
                          "created": 0,
                          "owned_by": "pocketlm"
                        }
                      ]
                    }
                    """.trimIndent()
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                }

                uri == "/v1/chat/completions" && method == Method.POST -> {
                    // TODO: parse body, call llama.cpp, stream response
                    val body = """
                    {
                      "id": "chatcmpl-stub",
                      "object": "chat.completion",
                      "created": ${System.currentTimeMillis() / 1000},
                      "model": "pocketlm-stub",
                      "choices": [{
                        "index": 0,
                        "message": {
                          "role": "assistant",
                          "content": "llama.cpp not wired yet. This is a stub response."
                        },
                        "finish_reason": "stop"
                      }],
                      "usage": {
                        "prompt_tokens": 0,
                        "completion_tokens": 0,
                        "total_tokens": 0
                      }
                    }
                    """.trimIndent()
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                }

                uri == "/health" || uri == "/" -> {
                    newFixedLengthResponse(Response.Status.OK, "text/plain", "pocketLM ok")
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
                }
            }
        }
    }
}

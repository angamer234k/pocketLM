package com.angamer.pocketlm.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatLine(val id: Long, val role: String, val text: String)

/** Process-wide chat so switching tabs does not wipe the thread. */
object ChatHistory {
    private const val PREF = "pocketlm_prefs"
    private const val KEY = "chat_history"
    private const val MAX_LINES = 200

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

    fun load(context: Context) {
        if (_lines.value.isNotEmpty()) return
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatLine(
                            id = o.optLong("id", i.toLong()),
                            role = o.optString("role", "user"),
                            text = o.optString("text", "")
                        )
                    )
                }
            }
            _lines.value = list
        } catch (_: Exception) {
            _lines.value = emptyList()
        }
    }

    fun set(context: Context, next: List<ChatLine>) {
        val trimmed = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
        _lines.value = trimmed
        persist(context, trimmed)
    }

    fun clear(context: Context) {
        _lines.value = emptyList()
        persist(context, emptyList())
    }

    private fun persist(context: Context, lines: List<ChatLine>) {
        val arr = JSONArray()
        for (line in lines) {
            arr.put(
                JSONObject()
                    .put("id", line.id)
                    .put("role", line.role)
                    .put("text", line.text)
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}

package com.angamer.pocketlm.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ApiKey(
    val id: String,
    val name: String,
    val secret: String,
    val plan: String,
    val rpm: Int,
    val enabled: Boolean
)

object ApiKeys {
    private const val PREF = "pocketlm_prefs"
    private const val FIELD = "api_keys"
    private val hits = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun list(context: Context): List<ApiKey> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(FIELD, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ApiKey(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        secret = o.optString("secret"),
                        plan = o.optString("plan", "free"),
                        rpm = o.optInt("rpm", 30),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
        }
    }

    fun save(context: Context, keys: List<ApiKey>) {
        val arr = JSONArray()
        for (key in keys) {
            arr.put(
                JSONObject()
                    .put("id", key.id)
                    .put("name", key.name)
                    .put("secret", key.secret)
                    .put("plan", key.plan)
                    .put("rpm", key.rpm)
                    .put("enabled", key.enabled)
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(FIELD, arr.toString())
            .apply()
    }

    fun newSecret(): String = "sk-" + UUID.randomUUID().toString().replace("-", "")

    /**
     * Null means allowed. No keys configured means the server stays open.
     */
    fun check(context: Context, authorization: String?): String? {
        val keys = list(context)
        if (keys.isEmpty()) return null
        val token = authorization
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()
            .orEmpty()
        if (token.isEmpty()) return "missing API key"
        val key = keys.firstOrNull { it.secret == token } ?: return "invalid API key"
        if (!key.enabled) return "disabled API key"
        if (key.rpm > 0 && !allow(key.id, key.rpm)) return "rate limit"
        return null
    }

    private fun allow(id: String, rpm: Int): Boolean {
        val now = System.currentTimeMillis()
        val window = hits.getOrPut(id) { ArrayDeque() }
        synchronized(window) {
            while (window.isNotEmpty() && now - window.first() > 60_000) window.removeFirst()
            if (window.size >= rpm) return false
            window.addLast(now)
            return true
        }
    }
}

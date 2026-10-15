package com.angamer.pocketlm

import android.content.Context
import com.angamer.pocketlm.update.UpdateMode

object AppPrefs {
    private const val NAME = "pocketlm_prefs"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun port(context: Context) = sp(context).getInt("port", 9090)
    fun setPort(context: Context, value: Int) = sp(context).edit().putInt("port", value).apply()

    fun nCtx(context: Context) = sp(context).getInt("n_ctx", 2048)
    fun setNCtx(context: Context, value: Int) = sp(context).edit().putInt("n_ctx", value).apply()

    fun threads(context: Context): Int {
        val stored = sp(context).getInt("threads", -1)
        if (stored > 0) return stored
        val auto = (Runtime.getRuntime().availableProcessors()).coerceIn(2, 8)
        setThreads(context, auto)
        return auto
    }

    fun setThreads(context: Context, value: Int) = sp(context).edit().putInt("threads", value).apply()

    fun gpuLayers(context: Context) = sp(context).getInt("gpu_layers", 0)
    fun setGpuLayers(context: Context, value: Int) =
        sp(context).edit().putInt("gpu_layers", value.coerceAtLeast(0)).apply()

    fun temperature(context: Context) = sp(context).getFloat("temp", 0.7f)
    fun setTemperature(context: Context, value: Float) =
        sp(context).edit().putFloat("temp", value).apply()

    fun maxTokens(context: Context) = sp(context).getInt("max_tokens", 256)
    fun setMaxTokens(context: Context, value: Int) =
        sp(context).edit().putInt("max_tokens", value).apply()

    fun updateMode(context: Context): UpdateMode =
        UpdateMode.from(sp(context).getString("update_mode", UpdateMode.OFF.name))

    fun setUpdateMode(context: Context, mode: UpdateMode) =
        sp(context).edit().putString("update_mode", mode.name).apply()
}

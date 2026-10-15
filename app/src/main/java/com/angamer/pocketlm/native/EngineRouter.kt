package com.angamer.pocketlm.native

import android.content.Context

enum class ActiveEngine { NONE, LLAMA, LITERT }

/**
 * Routes load / complete between llama.cpp and LiteRT based on file type.
 */
object EngineRouter {
    @Volatile
    var active: ActiveEngine = ActiveEngine.NONE
        private set

    fun load(
        context: Context,
        path: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int
    ): Boolean {
        unload()
        return if (LiteRtBridge.isLiteRtFile(path)) {
            val ok = LiteRtBridge.load(context, path)
            active = if (ok) ActiveEngine.LITERT else ActiveEngine.NONE
            ok
        } else {
            val ok = LlamaBridge.loadModel(path, nCtx, nThreads, nGpuLayers)
            active = if (ok) ActiveEngine.LLAMA else ActiveEngine.NONE
            ok
        }
    }

    fun unload() {
        LlamaBridge.unloadModel()
        LiteRtBridge.unload()
        active = ActiveEngine.NONE
    }

    fun isLoaded(): Boolean = when (active) {
        ActiveEngine.LLAMA -> LlamaBridge.isLoaded()
        ActiveEngine.LITERT -> LiteRtBridge.isLoaded()
        ActiveEngine.NONE -> false
    }

    fun abort() {
        LlamaBridge.abort()
        LiteRtBridge.abort()
    }

    fun backendInfo(): String = when (active) {
        ActiveEngine.LLAMA -> LlamaBridge.backendInfo()
        ActiveEngine.LITERT -> LiteRtBridge.backendInfo()
        ActiveEngine.NONE -> "no engine"
    }

    fun complete(prompt: String, maxTokens: Int, temperature: Float): String = when (active) {
        ActiveEngine.LLAMA -> LlamaBridge.complete(prompt, maxTokens, temperature)
        ActiveEngine.LITERT -> LiteRtBridge.complete(prompt, maxTokens)
        ActiveEngine.NONE -> "[error] no model loaded"
    }

    fun completeStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback
    ) {
        when (active) {
            ActiveEngine.LLAMA -> LlamaBridge.completeStream(prompt, maxTokens, temperature, callback)
            ActiveEngine.LITERT -> LiteRtBridge.completeStream(prompt, maxTokens, callback)
            ActiveEngine.NONE -> callback.onError("no model loaded")
        }
    }
}

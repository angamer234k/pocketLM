package com.angamer.pocketlm.native

object LlamaBridge {
    init {
        System.loadLibrary("pocketlm")
    }

    external fun nativeHello(): String

    /** Load a GGUF model from absolute path. */
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Boolean

    external fun nativeUnloadModel()

    external fun nativeIsLoaded(): Boolean

    /**
     * Generate completion for a raw prompt string.
     * For chat, pass a formatted prompt (or we'll add chat template later).
     */
    external fun nativeComplete(prompt: String, maxTokens: Int, temperature: Float): String

    fun loadModel(path: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean =
        nativeLoadModel(path, nCtx, nThreads)

    fun unloadModel() = nativeUnloadModel()

    fun isLoaded(): Boolean = nativeIsLoaded()

    fun complete(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String =
        nativeComplete(prompt, maxTokens, temperature)
}

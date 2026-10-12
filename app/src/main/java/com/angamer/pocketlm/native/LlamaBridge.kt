package com.angamer.pocketlm.native

interface TokenCallback {
    fun onToken(token: String)
    fun onDone()
    fun onError(message: String)
}

object LlamaBridge {
    init {
        System.loadLibrary("pocketlm")
    }

    external fun nativeHello(): String
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Boolean
    external fun nativeUnloadModel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeAbort()
    external fun nativeComplete(prompt: String, maxTokens: Int, temperature: Float): String
    external fun nativeCompleteStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback
    )

    fun loadModel(path: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean =
        nativeLoadModel(path, nCtx, nThreads)

    fun unloadModel() = nativeUnloadModel()

    fun isLoaded(): Boolean = nativeIsLoaded()

    fun abort() = nativeAbort()

    fun complete(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String =
        nativeComplete(prompt, maxTokens, temperature)

    fun completeStream(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        callback: TokenCallback
    ) = nativeCompleteStream(prompt, maxTokens, temperature, callback)
}

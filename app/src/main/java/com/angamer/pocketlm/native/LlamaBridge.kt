package com.angamer.pocketlm.native

object LlamaBridge {
    init {
        System.loadLibrary("pocketlm")
    }

    external fun nativeHello(): String

    // Future:
    // external fun loadModel(path: String): Boolean
    // external fun unloadModel()
    // external fun chatCompletion(messagesJson: String, maxTokens: Int, temperature: Float): String
}

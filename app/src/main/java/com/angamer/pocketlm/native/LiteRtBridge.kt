package com.angamer.pocketlm.native

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LiteRT / MediaPipe GenAI path.
 * Accepts MediaPipe LLM model files (often `.bin` / `.task` style packages).
 * Falls back with a clear error if the MediaPipe tasks-genai API is missing at runtime.
 */
object LiteRtBridge {
    private const val TAG = "LiteRtBridge"

    @Volatile private var engine: Any? = null
    @Volatile private var modelPath: String? = null
    private val abort = AtomicBoolean(false)

    fun isLiteRtFile(path: String): Boolean {
        val name = path.lowercase()
        return name.endsWith(".task") ||
            name.endsWith(".bin") ||
            name.endsWith(".tflite") ||
            name.endsWith(".litertlm")
    }

    fun isLoaded(): Boolean = engine != null

    fun loadedName(): String = modelPath?.substringAfterLast('/') ?: "none"

    fun unload() {
        abort.set(true)
        try {
            engine?.javaClass?.getMethod("close")?.invoke(engine)
        } catch (_: Throwable) {
        }
        engine = null
        modelPath = null
    }

    fun load(context: Context, path: String): Boolean {
        unload()
        abort.set(false)
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "missing $path")
            return false
        }
        return try {
            // Nested Java class name: Outer$Inner
            val optionsClassName =
                "com.google.mediapipe.tasks.genai.llminference.LlmInference" +
                    "$" +
                    "LlmInferenceOptions"
            val optionsClass = Class.forName(optionsClassName)
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            builder.javaClass.getMethod("setModelPath", String::class.java).invoke(builder, path)
            val options = builder.javaClass.getMethod("build").invoke(builder)

            val llmClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference"
            )
            val create = llmClass.getMethod(
                "createFromOptions",
                Context::class.java,
                optionsClass
            )
            engine = create.invoke(null, context.applicationContext, options)
            modelPath = path
            Log.i(TAG, "LiteRT/MediaPipe model loaded: $path")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "LiteRT load failed", t)
            engine = null
            modelPath = null
            false
        }
    }

    fun abort() {
        abort.set(true)
    }

    fun complete(prompt: String, maxTokens: Int = 256): String {
        val eng = engine ?: return "[error] no LiteRT model loaded"
        return try {
            abort.set(false)
            val methods = eng.javaClass.methods
            val gen = methods.firstOrNull { it.name == "generateResponse" && it.parameterTypes.size == 1 }
                ?: return "[error] generateResponse missing"
            val result = gen.invoke(eng, prompt) as? String
            result?.take(maxTokens * 8) ?: ""
        } catch (t: Throwable) {
            Log.e(TAG, "complete failed", t)
            "[error] ${t.message}"
        }
    }

    fun completeStream(prompt: String, maxTokens: Int, callback: TokenCallback) {
        try {
            val text = complete(prompt, maxTokens)
            if (text.startsWith("[error]")) {
                callback.onError(text.removePrefix("[error] "))
            } else {
                val step = 24
                var i = 0
                while (i < text.length && !abort.get()) {
                    val end = (i + step).coerceAtMost(text.length)
                    callback.onToken(text.substring(i, end))
                    i = end
                }
                callback.onDone()
            }
        } catch (t: Throwable) {
            callback.onError(t.message ?: "LiteRT error")
        }
    }

    fun backendInfo(): String =
        if (isLoaded()) "LiteRT/MediaPipe · ${loadedName()}" else "LiteRT idle"
}

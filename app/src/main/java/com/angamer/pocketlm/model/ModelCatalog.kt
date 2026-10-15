package com.angamer.pocketlm.model

import java.util.Locale

enum class ModelRuntime { LLAMA_CPP, LITERT }

data class CatalogModel(
    val id: String,
    val name: String,
    val approxMb: Int,
    val url: String,
    val filename: String,
    val runtime: ModelRuntime = ModelRuntime.LLAMA_CPP,
    val downloadable: Boolean = true,
    val note: String = ""
)

fun formatMb(mb: Int): String {
    val gb = mb / 1024.0
    val gbText = if (gb >= 1.0) {
        String.format(Locale.US, "%.2fGB", gb)
    } else {
        String.format(Locale.US, "%.3fGB", gb)
    }
    return "${mb}MB / $gbText"
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mbText = String.format(Locale.US, "%.0fMB", mb)
    val gbText = if (gb >= 1.0) {
        String.format(Locale.US, "%.2fGB", gb)
    } else {
        String.format(Locale.US, "%.3fGB", gb)
    }
    return "$mbText / $gbText"
}

fun CatalogModel.tags(): List<String> {
    val out = mutableListOf<String>()
    if (runtime == ModelRuntime.LLAMA_CPP && approxMb <= 500) {
        out += "Recommended for your device"
    }
    if (approxMb <= 500) out += "Fast"
    if (approxMb >= 1000) out += "Large"
    out += if (runtime == ModelRuntime.LITERT) "LiteRT" else "llama.cpp"
    out += formatMb(approxMb)
    return out
}

object ModelCatalog {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "smollm2-360m-q4",
            name = "SmolLM2 360M Instruct",
            approxMb = 230,
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
            filename = "smollm2-360m-instruct-q4_k_m.gguf",
            note = "smallest, lowest RAM"
        ),
        CatalogModel(
            id = "qwen25-05b-q4",
            name = "Qwen2.5 0.5B Instruct",
            approxMb = 400,
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            note = "best starter"
        ),
        CatalogModel(
            id = "llama32-1b-q4",
            name = "Llama 3.2 1B Instruct",
            approxMb = 800,
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            note = "needs a bit more RAM"
        ),
        CatalogModel(
            id = "qwen25-15b-q4",
            name = "Qwen2.5 1.5B Instruct",
            approxMb = 1000,
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            note = "~3GB free RAM"
        ),
        CatalogModel(
            id = "gemma2-2b-q4",
            name = "Gemma 2 2B Instruct",
            approxMb = 1600,
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            filename = "gemma-2-2b-it-Q4_K_M.gguf",
            note = "mid-range phones"
        ),
        CatalogModel(
            id = "litert-import",
            name = "LiteRT / MediaPipe model",
            approxMb = 500,
            url = "",
            filename = "",
            runtime = ModelRuntime.LITERT,
            downloadable = false,
            note = "Import a MediaPipe GenAI .bin/.task model. GGUF still uses llama.cpp."
        )
    )
}

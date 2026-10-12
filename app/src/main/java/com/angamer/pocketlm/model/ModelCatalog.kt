package com.angamer.pocketlm.model

data class CatalogModel(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val approxMb: Int,
    val url: String,
    val filename: String,
    val note: String = ""
)

/** Curated small GGUFs that fit phones. Direct HF resolve links. */
object ModelCatalog {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "qwen25-05b-q4",
            name = "Qwen2.5 0.5B Instruct",
            sizeLabel = "~400 MB",
            approxMb = 400,
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            note = "fastest starter"
        ),
        CatalogModel(
            id = "smollm2-360m-q4",
            name = "SmolLM2 360M Instruct",
            sizeLabel = "~230 MB",
            approxMb = 230,
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
            filename = "smollm2-360m-instruct-q4_k_m.gguf",
            note = "tiny / low RAM"
        ),
        CatalogModel(
            id = "llama32-1b-q4",
            name = "Llama 3.2 1B Instruct",
            sizeLabel = "~800 MB",
            approxMb = 800,
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            note = "good quality / small"
        ),
        CatalogModel(
            id = "qwen25-15b-q4",
            name = "Qwen2.5 1.5B Instruct",
            sizeLabel = "~1.0 GB",
            approxMb = 1000,
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            note = "needs ~3GB+ free RAM"
        ),
        CatalogModel(
            id = "gemma2-2b-q4",
            name = "Gemma 2 2B Instruct",
            sizeLabel = "~1.6 GB",
            approxMb = 1600,
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            filename = "gemma-2-2b-it-Q4_K_M.gguf",
            note = "heavier — mid-range phones"
        )
    )
}

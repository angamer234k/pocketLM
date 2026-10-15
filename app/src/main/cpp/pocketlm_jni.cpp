#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "pocketLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static int g_n_threads = 4;
static int g_n_gpu_layers = 0;
static std::atomic<bool> g_abort{false};

static void free_model_locked() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

static bool decode_prompt(const std::vector<llama_token> & tokens) {
    if (tokens.empty()) return true;
    // Batch the full prompt in one decode when possible — much faster than 1-token loops.
    const int n = (int) tokens.size();
    const int n_batch = llama_n_batch(g_ctx);
    int i = 0;
    while (i < n) {
        const int take = std::min(n_batch, n - i);
        llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(tokens.data() + i), take);
        if (llama_decode(g_ctx, batch) != 0) {
            return false;
        }
        i += take;
    }
    return true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring path_, jint n_ctx, jint n_threads, jint n_gpu_layers) {

    const char * path = env->GetStringUTFChars(path_, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);
    free_model_locked();
    g_abort = false;

    g_n_threads = n_threads > 0 ? n_threads : 4;
    g_n_gpu_layers = n_gpu_layers > 0 ? n_gpu_layers : 0;

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = g_n_gpu_layers;
    // Prefer mmap for lower RAM pressure on phones.
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(path_, path);

    if (!g_model) {
        LOGE("failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    cparams.n_batch = std::min(512, cparams.n_ctx);
    cparams.n_ubatch = cparams.n_batch;
    cparams.n_threads = g_n_threads;
    cparams.n_threads_batch = g_n_threads;
#ifdef LLAMA_FLASH_ATTN_TYPE_AUTO
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
#endif

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("model loaded ok, n_ctx=%d threads=%d gpu_layers=%d", cparams.n_ctx, g_n_threads, g_n_gpu_layers);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeUnloadModel(JNIEnv *, jobject) {
    g_abort = true;
    std::lock_guard<std::mutex> lock(g_mutex);
    free_model_locked();
    llama_backend_free();
    LOGI("model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeIsLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_model && g_ctx ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeAbort(JNIEnv *, jobject) {
    g_abort = true;
}

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeBackendInfo(JNIEnv *env, jobject) {
    // Vulkan is not linked in this build yet; CPU + optional gpu_layers when a GPU backend exists.
    char buf[192];
    snprintf(buf, sizeof(buf), "llama.cpp cpu · threads=%d · gpu_layers=%d · mmap=on",
             g_n_threads, g_n_gpu_layers);
    return env->NewStringUTF(buf);
}

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeComplete(
        JNIEnv *env, jobject /*thiz*/,
        jstring prompt_, jint max_tokens, jfloat temperature) {

    const char * prompt = env->GetStringUTFChars(prompt_, nullptr);
    if (!prompt) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort = false;

    if (!g_model || !g_ctx) {
        env->ReleaseStringUTFChars(path_, prompt);
        return env->NewStringUTF("[error] no model loaded");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt) + 8);
    int n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    }
    env->ReleaseStringUTFChars(prompt_, prompt);

    if (n < 0) return env->NewStringUTF("[error] tokenize failed");
    tokens.resize(n);

    llama_memory_clear(llama_get_memory(g_ctx), true);

    if (!decode_prompt(tokens)) {
        return env->NewStringUTF("[error] decode prompt failed");
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature > 0 ? temperature : 0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    const int max_gen = max_tokens > 0 ? max_tokens : 256;

    for (int i = 0; i < max_gen && !g_abort; i++) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) out.append(buf, n_chars);

        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeCompleteStream(
        JNIEnv *env, jobject /*thiz*/,
        jstring prompt_, jint max_tokens, jfloat temperature,
        jobject callback) {

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone = env->GetMethodID(cbClass, "onDone", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    if (!onToken || !onDone) {
        LOGE("callback methods missing");
        return;
    }

    const char * prompt = env->GetStringUTFChars(prompt_, nullptr);
    if (!prompt) {
        if (onError) {
            jstring err = env->NewStringUTF("empty prompt");
            env->CallVoidMethod(callback, onError, err);
            env->DeleteLocalRef(err);
        }
        return;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort = false;

    if (!g_model || !g_ctx) {
        env->ReleaseStringUTFChars(prompt_, prompt);
        if (onError) {
            jstring err = env->NewStringUTF("no model loaded");
            env->CallVoidMethod(callback, onError, err);
            env->DeleteLocalRef(err);
        }
        return;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt) + 8);
    int n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    }
    env->ReleaseStringUTFChars(prompt_, prompt);

    if (n < 0) {
        if (onError) {
            jstring err = env->NewStringUTF("tokenize failed");
            env->CallVoidMethod(callback, onError, err);
            env->DeleteLocalRef(err);
        }
        return;
    }
    tokens.resize(n);

    llama_memory_clear(llama_get_memory(g_ctx), true);

    if (!decode_prompt(tokens)) {
        if (onError) {
            jstring err = env->NewStringUTF("decode prompt failed");
            env->CallVoidMethod(callback, onError, err);
            env->DeleteLocalRef(err);
        }
        return;
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature > 0 ? temperature : 0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const int max_gen = max_tokens > 0 ? max_tokens : 256;

    for (int i = 0; i < max_gen && !g_abort; i++) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            std::string piece(buf, n_chars);
            jstring jpiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onToken, jpiece);
            env->DeleteLocalRef(jpiece);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                break;
            }
        }

        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);
    env->CallVoidMethod(callback, onDone);
}

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeHello(JNIEnv *env, jobject) {
    return env->NewStringUTF("pocketLM native + llama.cpp ready");
}

} // extern C

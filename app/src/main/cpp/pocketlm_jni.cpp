#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "pocketLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static int g_n_threads = 4;

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

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring path_, jint n_ctx, jint n_threads) {

    const char * path = env->GetStringUTFChars(path_, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);
    free_model_locked();

    g_n_threads = n_threads > 0 ? n_threads : 4;

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU for now; GPU later

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(path_, path);

    if (!g_model) {
        LOGE("failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    cparams.n_threads = g_n_threads;
    cparams.n_threads_batch = g_n_threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("model loaded ok, n_ctx=%d threads=%d", cparams.n_ctx, g_n_threads);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeUnloadModel(JNIEnv *, jobject) {
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

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeComplete(
        JNIEnv *env, jobject /*thiz*/,
        jstring prompt_, jint max_tokens, jfloat temperature) {

    const char * prompt = env->GetStringUTFChars(prompt_, nullptr);
    if (!prompt) {
        return env->NewStringUTF("");
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx) {
        env->ReleaseStringUTFChars(prompt_, prompt);
        return env->NewStringUTF("[error] no model loaded");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt ? strlen(prompt) + 8 : 8);
    int n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
    }
    env->ReleaseStringUTFChars(prompt_, prompt);

    if (n < 0) {
        return env->NewStringUTF("[error] tokenize failed");
    }
    tokens.resize(n);

    llama_memory_clear(llama_get_memory(g_ctx), true);

    // eval prompt
    for (int i = 0; i < n; i += 1) {
        llama_batch batch = llama_batch_get_one(&tokens[i], 1);
        if (llama_decode(g_ctx, batch) != 0) {
            return env->NewStringUTF("[error] decode prompt failed");
        }
    }

    // sampler
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature > 0 ? temperature : 0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    const int max_gen = max_tokens > 0 ? max_tokens : 256;

    for (int i = 0; i < max_gen; i++) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n_chars = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n_chars > 0) {
            out.append(buf, n_chars);
        }

        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }

    llama_sampler_free(smpl);

    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeHello(JNIEnv *env, jobject) {
    return env->NewStringUTF("pocketLM native + llama.cpp ready");
}

} // extern C

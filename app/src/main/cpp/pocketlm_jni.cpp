#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "pocketLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_angamer_pocketlm_native_LlamaBridge_nativeHello(JNIEnv *env, jobject /* this */) {
    LOGI("nativeHello called - llama.cpp not wired yet");
    return env->NewStringUTF("pocketLM native ready (llama.cpp coming next)");
}

// Future entry points:
// loadModel(path)
// unloadModel()
// chatCompletion(messagesJson, stream)
// etc.

}

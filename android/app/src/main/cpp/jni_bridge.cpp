#include "terminal_engine.h"
#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <mutex>

#define LOG_TAG "TXTerminalJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace tx;

// Global engine instance
static std::unique_ptr<TerminalEngine> g_engine;
static std::mutex g_engineMutex;
static jobject g_outputCallback = nullptr;
static JavaVM* g_javaVM = nullptr;

// JNI callback for output
void onTerminalOutput(const char* data, size_t length) {
    if (!g_javaVM || !g_outputCallback) {
        return;
    }

    JNIEnv* env;
    jint attachResult = g_javaVM->AttachCurrentThread(&env, nullptr);
    if (attachResult != JNI_OK) {
        LOGE("Failed to attach thread to JVM");
        return;
    }

    jclass callbackClass = env->GetObjectClass(g_outputCallback);
    if (!callbackClass) {
        LOGE("Failed to get callback class");
        g_javaVM->DetachCurrentThread();
        return;
    }

    jmethodID onOutputMethod = env->GetMethodID(callbackClass, "onOutput", "([B)V");
    if (!onOutputMethod) {
        LOGE("Failed to get onOutput method");
        env->DeleteLocalRef(callbackClass);
        g_javaVM->DetachCurrentThread();
        return;
    }

    jbyteArray byteArray = env->NewByteArray(length);
    env->SetByteArrayRegion(byteArray, 0, length, reinterpret_cast<const jbyte*>(data));

    env->CallVoidMethod(g_outputCallback, onOutputMethod, byteArray);

    env->DeleteLocalRef(byteArray);
    env->DeleteLocalRef(callbackClass);

    g_javaVM->DetachCurrentThread();
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_javaVM = vm;
    LOGD("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_initialize(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        g_engine = std::make_unique<TerminalEngine>();
        g_engine->initialize();
        g_engine->setOutputCallback(onTerminalOutput);
        LOGD("NativeTerminal initialized");
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_setUserspacePath(JNIEnv* env, jclass clazz,
                                                             jstring path) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_engine && path) {
        const char* pathStr = env->GetStringUTFChars(path, nullptr);
        g_engine->setUserspacePath(pathStr);
        env->ReleaseStringUTFChars(path, pathStr);
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_setOutputCallback(JNIEnv* env, jclass clazz,
                                                              jobject callback) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_outputCallback) {
        env->DeleteGlobalRef(g_outputCallback);
        g_outputCallback = nullptr;
    }

    if (callback) {
        g_outputCallback = env->NewGlobalRef(callback);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_executeCommand(JNIEnv* env, jclass clazz,
                                                           jstring command) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine || !command) {
        return JNI_FALSE;
    }

    const char* cmdStr = env->GetStringUTFChars(command, nullptr);
    bool result = g_engine->executeCommand(cmdStr);
    env->ReleaseStringUTFChars(command, cmdStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_executeWithLinker(JNIEnv* env, jclass clazz,
                                                              jstring linker,
                                                              jstring executable,
                                                              jobjectArray args) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine || !linker || !executable) {
        return JNI_FALSE;
    }

    const char* linkerStr = env->GetStringUTFChars(linker, nullptr);
    const char* execStr = env->GetStringUTFChars(executable, nullptr);

    std::vector<std::string> argList;
    if (args) {
        jsize len = env->GetArrayLength(args);
        for (jsize i = 0; i < len; i++) {
            jstring arg = (jstring) env->GetObjectArrayElement(args, i);
            if (arg) {
                const char* argStr = env->GetStringUTFChars(arg, nullptr);
                argList.push_back(argStr);
                env->ReleaseStringUTFChars(arg, argStr);
                env->DeleteLocalRef(arg);
            }
        }
    }

    bool result = g_engine->executeWithLinker(linkerStr, execStr, argList);

    env->ReleaseStringUTFChars(linker, linkerStr);
    env->ReleaseStringUTFChars(executable, execStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_sendInput(JNIEnv* env, jclass clazz,
                                                      jstring input) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine || !input) {
        return JNI_FALSE;
    }

    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    bool result = g_engine->sendInput(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_sendRawData(JNIEnv* env, jclass clazz,
                                                        jbyteArray data, jint length) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine || !data || length <= 0) {
        return JNI_FALSE;
    }

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    bool result = g_engine->sendRawData(reinterpret_cast<const char*>(bytes), length);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_resizeTerminal(JNIEnv* env, jclass clazz,
                                                           jint rows, jint cols) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return JNI_FALSE;
    }

    return g_engine->resizeTerminal(rows, cols) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_isProcessRunning(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return JNI_FALSE;
    }

    return g_engine->isProcessRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tx_terminal_native_NativeTerminal_getCurrentPid(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return -1;
    }

    return g_engine->getCurrentPid();
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_killProcess(JNIEnv* env, jclass clazz,
                                                        jint signal) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return JNI_FALSE;
    }

    return g_engine->killProcess(signal) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_addEnvironmentVariable(JNIEnv* env, jclass clazz,
                                                                   jstring name,
                                                                   jstring value) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_engine && name && value) {
        const char* nameStr = env->GetStringUTFChars(name, nullptr);
        const char* valueStr = env->GetStringUTFChars(value, nullptr);
        g_engine->addEnvironmentVariable(nameStr, valueStr);
        env->ReleaseStringUTFChars(name, nameStr);
        env->ReleaseStringUTFChars(value, valueStr);
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_clearEnvironmentVariables(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_engine) {
        g_engine->clearEnvironmentVariables();
    }
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_setUserspaceEnabled(JNIEnv* env, jclass clazz,
                                                                jboolean enabled) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_engine) {
        g_engine->setUserspaceEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tx_terminal_native_NativeTerminal_isUserspaceEnabled(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return JNI_FALSE;
    }

    return g_engine->isUserspaceEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_tx_terminal_native_NativeTerminal_getLastError(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_engine) {
        return env->NewStringUTF("Engine not initialized");
    }

    return env->NewStringUTF(g_engine->getLastError().c_str());
}

JNIEXPORT void JNICALL
Java_com_tx_terminal_native_NativeTerminal_cleanup(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_engine) {
        g_engine->cleanup();
        g_engine.reset();
    }

    if (g_outputCallback) {
        env->DeleteGlobalRef(g_outputCallback);
        g_outputCallback = nullptr;
    }

    LOGD("NativeTerminal cleanup complete");
}

} // extern "C"

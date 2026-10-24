#include <jni.h>
#include <cstdlib>
#include <string>
#include <vector>
#include "molite.h"

// All text crosses JNI as UTF-8 byte arrays, including supplementary Unicode.
static std::vector<uint8_t> bytes(JNIEnv* env, jbyteArray value) {
    auto size = env->GetArrayLength(value);
    std::vector<uint8_t> result(static_cast<size_t>(size) + 1);
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(result.data()));
    return result;
}
static void check(JNIEnv* env, int status) {
    if (status != MOLITE_OK) {
        auto message = "MOLite C ABI status " + std::to_string(status);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
    }
}
extern "C" {
#ifdef __ANDROID__
JNIEXPORT void JNICALL Java_io_github_ppisky_molite_NativeCore_androidInit(JNIEnv* env, jobject, jobject context) {
    check(env, molite_android_init(env, context));
}
#endif
JNIEXPORT jint JNICALL Java_io_github_ppisky_molite_NativeCore_abi(JNIEnv*, jobject) {
    return molite_abi_version();
}
JNIEXPORT jlong JNICALL Java_io_github_ppisky_molite_NativeCore_open(JNIEnv* env, jobject, jbyteArray path) {
    auto input = bytes(env, path); molite_engine handle = 0;
    check(env, molite_open(input.data(), input.size()-1, &handle)); return static_cast<jlong>(handle);
}
JNIEXPORT jlong JNICALL Java_io_github_ppisky_molite_NativeCore_submit(JNIEnv* env, jobject, jlong engine, jbyteArray json) {
    auto input = bytes(env, json); molite_task handle = 0;
    check(env, molite_submit(engine, input.data(), input.size()-1, &handle)); return static_cast<jlong>(handle);
}
JNIEXPORT jbyteArray JNICALL Java_io_github_ppisky_molite_NativeCore_next(JNIEnv* env, jobject, jlong task) {
    molite_buffer buffer{};
    int status = molite_task_next(task, 100, &buffer);
    if (status == MOLITE_EMPTY) return nullptr;
    if (status != MOLITE_OK) { check(env, status); return nullptr; }
    auto result = env->NewByteArray(static_cast<jsize>(buffer.len));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(buffer.len), reinterpret_cast<jbyte*>(buffer.data));
    molite_buffer_free(buffer); // exactly once, even if Java allocation fails
    return result;
}
JNIEXPORT void JNICALL Java_io_github_ppisky_molite_NativeCore_cancel(JNIEnv* env, jobject, jlong task) { check(env, molite_task_cancel(task)); }
JNIEXPORT void JNICALL Java_io_github_ppisky_molite_NativeCore_release(JNIEnv* env, jobject, jlong task) { check(env, molite_task_release(task)); }
JNIEXPORT void JNICALL Java_io_github_ppisky_molite_NativeCore_close(JNIEnv* env, jobject, jlong engine) { check(env, molite_close(engine)); }
JNIEXPORT void JNICALL Java_io_github_ppisky_molite_NativeCore_secret(JNIEnv* env, jobject, jbyteArray name, jbyteArray value) {
    auto key = bytes(env, name); auto secret = bytes(env, value);
#ifdef _WIN32
    int result = _putenv_s(reinterpret_cast<char*>(key.data()), reinterpret_cast<char*>(secret.data()));
#else
    int result = secret.size() == 1 ? unsetenv(reinterpret_cast<char*>(key.data())) :
        setenv(reinterpret_cast<char*>(key.data()), reinterpret_cast<char*>(secret.data()), 1);
#endif
    volatile uint8_t* wipe = secret.data();
    for (size_t i = 0; i < secret.size(); ++i) wipe[i] = 0;
    if (result) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Cannot set session credential");
}
}

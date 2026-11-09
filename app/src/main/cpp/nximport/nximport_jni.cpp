// JNI of the on-device importer (libnximport.so, loaded by the launcher process only):
//   NativeImporter.run(String outDir, String[] names, int[] fds, String titleId, boolean verify, Callback cb) -> int
//   NativeImporter.cancel()
// cb.progress(String phase, long done, long total, String item) at most every 200 ms; cb.log(String line).
// The returned code is 0 on success; NativeImporter.lastError() has the message otherwise (never key material).
#include <android/log.h>
#include <jni.h>

#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#include "nximport.h"

namespace {
std::atomic<bool> g_cancel{false};
std::mutex g_err_m;
std::string g_error;

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}
}  // namespace

// The Kotlin class is com.dev.tsukihimeandroid.NativeImporter; the CMake target defines the JNI package.
#ifndef NXI_JNI_PKG
#define NXI_JNI_PKG com_dev_tsukihimeandroid
#endif
#define NXI_CAT2(a, b) a##b
#define NXI_CAT(a, b) NXI_CAT2(a, b)
#define JNI_FN(name) NXI_CAT(NXI_CAT(NXI_CAT(Java_, NXI_JNI_PKG), _NativeImporter_), name)

extern "C" {

JNIEXPORT jint JNICALL JNI_FN(run)(JNIEnv* env, jobject, jstring out_dir, jobjectArray names, jintArray fds, jstring title,
                                   jboolean verify, jobject cb) {
    g_cancel = false;
    {
        std::lock_guard lk(g_err_m);
        g_error.clear();
    }
    nxi::Options opt;
    opt.out_dir = str(env, out_dir);
    opt.title_id = strtoull(str(env, title).c_str(), nullptr, 16);
    opt.verify_only = verify;
    opt.apply_update = true;  // apply a matching official update when one is present; base-only dumps remain valid
    opt.cancel = &g_cancel;
    std::vector<nxi::InputFile> inputs;
    jsize n = names ? env->GetArrayLength(names) : 0;
    jsize fd_count = fds ? env->GetArrayLength(fds) : 0;
    if (n != fd_count) {
        std::lock_guard lk(g_err_m);
        g_error = "JNI input-name and file-descriptor arrays have different lengths.";
        return -2;
    }
    std::vector<jint> fd(size_t(n > 0 ? n : 0));
    if (n > 0) env->GetIntArrayRegion(fds, 0, n, fd.data());
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        std::lock_guard lk(g_err_m);
        g_error = "Could not read JNI file-descriptor array.";
        return -3;
    }
    for (jsize i = 0; i < n; i++) {
        auto s = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        inputs.push_back({str(env, s), fd[size_t(i)]});
        env->DeleteLocalRef(s);
    }
    jclass cls = cb ? env->GetObjectClass(cb) : nullptr;
    jmethodID progress = cls ? env->GetMethodID(cls, "progress", "(Ljava/lang/String;JJLjava/lang/String;)V") : nullptr;
    jmethodID log = cls ? env->GetMethodID(cls, "log", "(Ljava/lang/String;)V") : nullptr;
    if (env->ExceptionCheck()) env->ExceptionClear();
    opt.on_progress = [&](const nxi::Progress& p) {
        if (!progress) return;
        jstring ph = env->NewStringUTF(p.phase.c_str()), it = env->NewStringUTF(p.item.c_str());
        env->CallVoidMethod(cb, progress, ph, jlong(p.done), jlong(p.total), it);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(ph);
        env->DeleteLocalRef(it);
    };
    opt.on_log = [&](const std::string& line) {
        __android_log_print(ANDROID_LOG_INFO, "nximport", "%s", line.c_str());
        if (!log) return;
        jstring s = env->NewStringUTF(line.c_str());
        env->CallVoidMethod(cb, log, s);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(s);
    };
    std::string err;
    int rc = nxi::run_import(inputs, opt, err);
    if (rc) __android_log_print(ANDROID_LOG_WARN, "nximport", "import failed (%d): %s", rc, err.c_str());
    std::lock_guard lk(g_err_m);
    g_error = err;
    return rc;
}

JNIEXPORT void JNICALL JNI_FN(cancel)(JNIEnv*, jobject) { g_cancel = true; }

JNIEXPORT jstring JNICALL JNI_FN(lastError)(JNIEnv* env, jobject) {
    std::lock_guard lk(g_err_m);
    return env->NewStringUTF(g_error.c_str());
}

}  // extern "C"

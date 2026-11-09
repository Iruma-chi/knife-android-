#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

struct knife_package_input {
    const char* name;
    int32_t fd;
};

extern "C" void knife_package_clear();
extern "C" int32_t knife_package_install(
    const knife_package_input* inputs, size_t input_count, uint64_t title_id,
    char* error, size_t error_capacity);

extern "C" JNIEXPORT void JNICALL
Java_com_dev_tsukihimeandroid_GameActivity_nativeClearPackage(JNIEnv*, jobject) {
    knife_package_clear();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dev_tsukihimeandroid_GameActivity_nativeOpenPackage(
    JNIEnv* env, jobject /* activity */, jobjectArray names, jintArray descriptors) {
    // Never allow a failed re-open to leave a previous package active in this process.
    knife_package_clear();
    if (!names || !descriptors) {
        return env->NewStringUTF("Package names or descriptors were not supplied.");
    }
    const jsize name_count = env->GetArrayLength(names);
    const jsize fd_count = env->GetArrayLength(descriptors);
    if (name_count <= 0 || name_count != fd_count || name_count > 64) {
        return env->NewStringUTF("Package input list is empty or inconsistent.");
    }

    std::vector<jint> fds(static_cast<size_t>(fd_count));
    env->GetIntArrayRegion(descriptors, 0, fd_count, fds.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<std::string> owned_names;
    owned_names.reserve(static_cast<size_t>(name_count));
    for (jsize i = 0; i < name_count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        if (!value) return env->NewStringUTF("A package input name was null.");
        const char* chars = env->GetStringUTFChars(value, nullptr);
        if (!chars) {
            env->DeleteLocalRef(value);
            return nullptr;
        }
        owned_names.emplace_back(chars);
        env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }

    std::vector<knife_package_input> inputs;
    inputs.reserve(owned_names.size());
    for (size_t i = 0; i < owned_names.size(); ++i) {
        if (owned_names[i].empty() || fds[i] < 0) {
            return env->NewStringUTF("A package input has an invalid name or descriptor.");
        }
        inputs.push_back({owned_names[i].c_str(), fds[i]});
    }

    char error[1024]{};
    const int32_t result = knife_package_install(inputs.data(), inputs.size(), 0, error, sizeof(error));
    return result == 0 ? nullptr : env->NewStringUTF(error[0] ? error : "Could not open the NSP/XCI package.");
}

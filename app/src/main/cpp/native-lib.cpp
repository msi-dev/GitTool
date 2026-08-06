#include <jni.h>
#include <string>
#include <fstream>
#include <sys/system_properties.h>
#include <unistd.h>
#include <dirent.h>
#include <android/log.h>

#define LOG_TAG "GitToolNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static bool fileExists(const char* path) { return access(path, F_OK) == 0; }

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msi_gittool_security_NativeSecurity_isRootedNative(JNIEnv*, jobject) {
    const char* paths[] = {
        "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
        "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su", "/vendor/bin/su"
    };
    for (const char* p : paths) if (fileExists(p)) return JNI_TRUE;
    if (fileExists("/sbin/magisk") || fileExists("/data/adb/magisk")) return JNI_TRUE;
    char tags[PROP_VALUE_MAX];
    __system_property_get("ro.build.tags", tags);
    if (strstr(tags, "test-keys")) return JNI_TRUE;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msi_gittool_security_NativeSecurity_isEmulatorNative(JNIEnv*, jobject) {
    char prop[PROP_VALUE_MAX];
    __system_property_get("ro.kernel.qemu", prop);
    if (strcmp(prop, "1") == 0) return JNI_TRUE;
    __system_property_get("ro.hardware", prop);
    if (strstr(prop, "goldfish") || strstr(prop, "ranchu")) return JNI_TRUE;
    __system_property_get("ro.product.model", prop);
    if (strstr(prop, "sdk") || strstr(prop, "google_sdk") || strstr(prop, "Emulator")) return JNI_TRUE;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msi_gittool_security_NativeSecurity_isDebuggerAttachedNative(JNIEnv*, jobject) {
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.find("TracerPid:") != std::string::npos) {
            int pid = 0;
            try {
                pid = std::stoi(line.substr(line.find(":") + 2));
            } catch (...) {
                pid = 0;
            }
            if (pid != 0) return JNI_TRUE;
            break;
        }
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_msi_gittool_security_NativeSecurity_isFridaRunningNative(JNIEnv*, jobject) {
    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        if (line.find("frida") != std::string::npos || line.find("gum-js-loop") != std::string::npos)
            return JNI_TRUE;
        if (line.find("linjector") != std::string::npos) return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msi_gittool_security_NativeSecurity_getNativeClientIdHex(JNIEnv* env, jobject) {
    return env->NewStringUTF("4f7632336c694c48584b6949685369553767336b");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_msi_gittool_security_NativeSecurity_getNativeClientSecretHex(JNIEnv* env, jobject) {
    return env->NewStringUTF("343033333937393638313633363333363536323133323635333033323635333336363334333136363634333633353338333836323331333333393333363333383337");
}

package com.msi.gittool.security

import android.os.Debug
import android.util.Log
import java.io.File

object NativeSecurity {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("gittool_security")
            isNativeLoaded = true
        } catch (e: Throwable) {
            Log.d("NativeSecurity", "Native security library not present, falling back to Kotlin security checks.")
            isNativeLoaded = false
        }
    }

    private external fun isRootedNative(): Boolean
    private external fun isEmulatorNative(): Boolean
    private external fun isDebuggerAttachedNative(): Boolean
    private external fun isFridaRunningNative(): Boolean
    private external fun getNativeClientIdHex(): String
    private external fun getNativeClientSecretHex(): String

    fun hexToString(hex: String): String {
        return try {
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                bytes[i] = hex.substring(index, index + 2).toInt(16).toByte()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun getClientId(): String {
        if (isNativeLoaded) {
            try {
                val hex = getNativeClientIdHex()
                val decoded = hexToString(hex)
                if (decoded.isNotEmpty()) return decoded
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Error fetching native client ID", e)
            }
        }
        return hexToString("4f7632336c694c48584b6949685369553767336b")
    }

    fun getClientSecret(): String {
        if (isNativeLoaded) {
            try {
                val hex = getNativeClientSecretHex()
                val decoded = hexToString(hex)
                if (decoded.isNotEmpty()) return decoded
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Error fetching native client secret", e)
            }
        }
        return hexToString("343033333937393638313633363333363536323133323635333033323635333336363334333136363634333633353338333836323331333333393333363333383337")
    }

    fun isRooted(): Boolean {
        if (isNativeLoaded) {
            try {
                return isRootedNative()
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Native isRooted check failed", e)
            }
        }
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su", "/vendor/bin/su"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) return true
            } catch (e: Throwable) {}
        }
        val tags = android.os.Build.TAGS
        if (tags != null && tags.contains("test-keys")) return true
        return false
    }

    fun isEmulator(): Boolean {
        if (isNativeLoaded) {
            try {
                return isEmulatorNative()
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Native isEmulator check failed", e)
            }
        }
        val fingerprint = android.os.Build.FINGERPRINT ?: ""
        val model = android.os.Build.MODEL ?: ""
        val hardware = android.os.Build.HARDWARE ?: ""
        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
    }

    fun isDebuggerAttached(): Boolean {
        if (isNativeLoaded) {
            try {
                return isDebuggerAttachedNative()
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Native isDebuggerAttached check failed", e)
            }
        }
        return Debug.isDebuggerConnected()
    }

    fun isFridaRunning(): Boolean {
        if (isNativeLoaded) {
            try {
                return isFridaRunningNative()
            } catch (e: Throwable) {
                Log.e("NativeSecurity", "Native isFridaRunning check failed", e)
            }
        }
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                var found = false
                mapsFile.forEachLine { line ->
                    if (line.contains("frida") || line.contains("gum-js-loop") || line.contains("linjector")) {
                        found = true
                    }
                }
                return found
            }
        } catch (e: Throwable) {}
        return false
    }
}

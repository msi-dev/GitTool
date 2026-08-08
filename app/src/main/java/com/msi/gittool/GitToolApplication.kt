package com.msi.gittool

import android.app.Application
import android.os.Process
import android.util.Log
import com.msi.gittool.analytics.OAuthCrashReporter
import com.msi.gittool.di.AppContainer
import com.msi.gittool.di.DefaultAppContainer
import com.msi.gittool.security.IntegrityChecker
import com.msi.gittool.security.NativeSecurity
import com.msi.gittool.security.PlayIntegrityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GitToolApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        // Initialize the app container first to ensure the app UI and MainActivity are fully supported
        try {
            container = DefaultAppContainer(this)
            OAuthCrashReporter.initialize(container.repoRepository)
        } catch (e: Throwable) {
            Log.e("GitToolApp", "Failed to initialize DefaultAppContainer", e)
        }

        // Run security diagnostics safely on a background thread without interfering with startup health
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!BuildConfig.DEBUG) {
                    if (!IntegrityChecker.isSignatureValid(this@GitToolApplication)) {
                        Log.e("GitToolSec", "Signature invalid!")
                    }
                    if (NativeSecurity.isDebuggerAttached()) {
                        Log.e("GitToolSec", "Debugger detected!")
                    }
                    if (NativeSecurity.isFridaRunning()) {
                        Log.e("GitToolSec", "Hooking framework detected!")
                    }
                }
                Log.d("GitToolSec", "Rooted: ${NativeSecurity.isRooted()}, Emulator: ${NativeSecurity.isEmulator()}")
            } catch (e: Throwable) {
                Log.e("GitToolSec", "Diagnostics encountered an error", e)
            }
        }
    }
}


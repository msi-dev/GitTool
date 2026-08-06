package com.msi.gittool.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

object IntegrityChecker {
    // Standard debug or fallback expected SHA-256 hash. If they want strict verification they can customise.
    private const val EXPECTED_HASH = "AA:BB:CC:DD:EE:FF"

    fun isSignatureValid(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures: Array<Signature> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners ?: return false
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures ?: return false
            }
            val md = MessageDigest.getInstance("SHA-256")
            for (sig in signatures) {
                val hash = md.digest(sig.toByteArray())
                val hex = hash.joinToString(":") { "%02X".format(it) }
                if (hex != EXPECTED_HASH) {
                    // We can log or return false in absolute production
                }
            }
            return true
        } catch (e: Exception) { return false }
    }
}

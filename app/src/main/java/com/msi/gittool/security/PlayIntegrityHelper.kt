package com.msi.gittool.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object PlayIntegrityHelper {
    suspend fun getToken(context: Context): String? {
        return try {
            val integrityManager = IntegrityManagerFactory.create(context)
            // Generate a secure challenge/nonce
            val nonce = "GitTool_" + System.currentTimeMillis()
            
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()

            suspendCancellableCoroutine { cont ->
                integrityManager.requestIntegrityToken(request)
                    .addOnSuccessListener { response ->
                        cont.resume(response.token())
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            }
        } catch (e: Throwable) {
            null
        }
    }
}

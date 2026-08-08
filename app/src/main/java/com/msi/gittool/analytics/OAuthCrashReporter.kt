package com.msi.gittool.analytics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.msi.gittool.data.repository.RepoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data representation of a failed OAuth redirection attempt or authentication failure.
 */
data class OAuthFailureEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val stage: String,            // e.g. "AUTH_LAUNCH", "REDIRECT_PARSE", "CODE_EXCHANGE", "CANCELLED", "MALFORMED_INTENT"
    val errorType: String,        // e.g. "AUTH_EXCEPTION", "HTTP_ERROR", "INVALID_URI", "OAUTH_REDIRECT_PARAM_ERROR"
    val errorMessage: String,
    val sanitizedUri: String? = null,
    val exceptionClassName: String? = null,
    val stackTraceSnippet: String? = null,
    val extraKeys: Map<String, String> = emptyMap()
) {
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMs))
}

/**
 * Custom Exception thrown or passed to Firebase Crashlytics to record failed OAuth redirection attempts as non-fatals.
 */
class OAuthRedirectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Service/Logger mechanism responsible for capturing, formatting, sanitizing, and reporting
 * failed OAuth redirection attempts to Crash Analytics services (Firebase Crashlytics),
 * system Logcat, and local app activity logs.
 */
object OAuthCrashReporter {
    private const val TAG = "OAuthCrashReporter"
    private const val MAX_LOG_BUFFER = 50

    private val _failureLogs = MutableStateFlow<List<OAuthFailureEvent>>(emptyList())
    val failureLogs: StateFlow<List<OAuthFailureEvent>> = _failureLogs.asStateFlow()

    private var repoRepository: RepoRepository? = null

    fun initialize(repository: RepoRepository? = null) {
        this.repoRepository = repository
    }

    /**
     * Captures and reports a failed OAuth redirection attempt or authentication issue.
     * Logs details to Firebase Crashlytics (with custom key-values and recorded exception),
     * Logcat, and local in-memory/repository activity history.
     */
    fun reportOAuthFailure(
        stage: String,
        errorType: String,
        errorMessage: String,
        throwable: Throwable? = null,
        uri: Uri? = null,
        extraKeys: Map<String, String> = emptyMap()
    ) {
        val sanitizedUriString = sanitizeUri(uri)
        val stackSnippet = throwable?.stackTraceToString()?.take(500)

        val failureEvent = OAuthFailureEvent(
            stage = stage,
            errorType = errorType,
            errorMessage = errorMessage,
            sanitizedUri = sanitizedUriString,
            exceptionClassName = throwable?.javaClass?.simpleName ?: "OAuthRedirectionException",
            stackTraceSnippet = stackSnippet,
            extraKeys = extraKeys
        )

        // 1. Log to Android System Logcat
        Log.e(
            TAG,
            "OAuth Redirection/Auth Failure [$stage | $errorType]: $errorMessage | URI: $sanitizedUriString",
            throwable
        )

        // 2. Report to Crash Analytics (Firebase Crashlytics)
        reportToCrashlytics(failureEvent, throwable)

        // 3. Store in local StateFlow for in-app developer/UI diagnostics
        val currentLogs = _failureLogs.value.toMutableList()
        currentLogs.add(0, failureEvent)
        if (currentLogs.size > MAX_LOG_BUFFER) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _failureLogs.value = currentLogs

        // 4. Log to Repo Activity audit trail if available
        repoRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo.logActivity(
                        type = "OAUTH_FAILED",
                        title = "OAuth Redirection Failed: $stage",
                        message = errorMessage,
                        details = "[$errorType] URI: ${sanitizedUriString ?: "N/A"}",
                        status = "ERROR"
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not persist OAuth failure to repository activity database: ${e.message}")
                }
            }
        }
    }

    private fun reportToCrashlytics(event: OAuthFailureEvent, throwable: Throwable?) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Attach rich context metadata keys to the Crashlytics session
            crashlytics.setCustomKey("oauth_stage", event.stage)
            crashlytics.setCustomKey("oauth_error_type", event.errorType)
            crashlytics.setCustomKey("oauth_error_msg", event.errorMessage)
            event.sanitizedUri?.let { crashlytics.setCustomKey("oauth_sanitized_uri", it) }
            event.exceptionClassName?.let { crashlytics.setCustomKey("oauth_exception_class", it) }
            crashlytics.setCustomKey("oauth_failure_timestamp", event.timestampMs)

            event.extraKeys.forEach { (key, value) ->
                crashlytics.setCustomKey("oauth_meta_$key", value)
            }

            // Write custom breadcrumb log to Crashlytics
            crashlytics.log("[OAuthCrashReporter] Failed redirection attempt in stage '${event.stage}': ${event.errorMessage}")

            // Record as a non-fatal exception in Crashlytics
            val exceptionToRecord = throwable ?: OAuthRedirectionException(
                "OAuth Redirection Failed [${event.stage} | ${event.errorType}]: ${event.errorMessage}"
            )
            crashlytics.recordException(exceptionToRecord)
        } catch (e: Throwable) {
            // Gracefully handle environments without initialized Firebase Crashlytics
            Log.w(TAG, "Firebase Crashlytics not initialized or unavailable: ${e.message}")
        }
    }

    /**
     * Sanitizes authorization callback URIs by redacting sensitive parameters like code, token, state.
     */
    fun sanitizeUri(uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { name ->
                if (name.equals("code", ignoreCase = true) ||
                    name.equals("access_token", ignoreCase = true) ||
                    name.equals("token", ignoreCase = true) ||
                    name.equals("state", ignoreCase = true) ||
                    name.equals("client_secret", ignoreCase = true)
                ) {
                    builder.appendQueryParameter(name, "[REDACTED]")
                } else {
                    val value = uri.getQueryParameter(name)
                    builder.appendQueryParameter(name, value)
                }
            }
            builder.build().toString()
        } catch (e: Throwable) {
            "${uri.scheme}://${uri.host ?: ""}/[URI_SANITIZATION_FAILED]"
        }
    }

    fun clearLogs() {
        _failureLogs.value = emptyList()
    }
}

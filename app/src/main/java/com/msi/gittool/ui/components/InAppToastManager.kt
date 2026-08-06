package com.msi.gittool.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false,
    val durationMs: Long = 3000L
)

object InAppToastManager {
    private val _toastState = MutableStateFlow<ToastMessage?>(null)
    val toastState: StateFlow<ToastMessage?> = _toastState.asStateFlow()

    fun showToast(message: String, isError: Boolean = false, durationMs: Long = 3000L) {
        if (message.isBlank()) return
        _toastState.value = ToastMessage(
            message = message,
            isError = isError,
            durationMs = durationMs
        )
    }

    fun dismiss() {
        _toastState.value = null
    }
}

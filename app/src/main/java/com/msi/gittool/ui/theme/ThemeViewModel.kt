package com.msi.gittool.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.local.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val themePreferences: ThemePreferences) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeModeFlow
        .map { ThemeMode.fromValue(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.saveThemeMode(mode.value)
        }
    }

    companion object {
        fun Factory(themePreferences: ThemePreferences): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(themePreferences) as T
            }
        }
    }
}

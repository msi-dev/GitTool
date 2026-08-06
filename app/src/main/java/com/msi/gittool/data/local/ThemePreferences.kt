package com.msi.gittool.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gittool_theme_settings")

class ThemePreferences(private val context: Context) {

    companion object {
        private val THEME_KEY = intPreferencesKey("user_theme_mode")
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: 0 // Default: 0 (System)
    }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode
        }
    }
}

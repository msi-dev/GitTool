package com.msi.gittool.ui.theme

enum class ThemeMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);
    
    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

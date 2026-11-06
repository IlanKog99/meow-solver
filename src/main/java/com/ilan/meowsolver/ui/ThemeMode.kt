package com.ilan.meowsolver.ui

/** App (setup screen) appearance. The overlay card is always dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    fun isDark(systemDark: Boolean) = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val PREF_KEY = "themeMode"
        fun fromPref(v: String?) = entries.firstOrNull { it.name == v } ?: SYSTEM
    }
}

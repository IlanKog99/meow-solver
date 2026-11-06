package com.ilan.meowsolver

import com.ilan.meowsolver.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun systemFollowsPhone() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
    }
    @Test fun explicitModesIgnorePhone() {
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
    }
    @Test fun prefParsingDefaultsToSystem() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromPref("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("purple"))
    }
}

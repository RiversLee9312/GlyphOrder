package dev.glyphorder.app.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test fun permissionDeniedFallsBackWithoutCrashing() {
        val actions = mutableListOf<String>()
        assertTrue(SettingsNavigation.attempt { action ->
            actions += action
            if (actions.size == 1) throw SecurityException("OEM permission")
        })
        assertEquals(listOf("android.settings.DISPLAY_SETTINGS", "android.settings.SETTINGS"), actions)
    }
    @Test fun unavailableSettingsReturnsFailureForUserFeedback() {
        assertFalse(SettingsNavigation.attempt { throw IllegalStateException("No settings activity") })
    }
}

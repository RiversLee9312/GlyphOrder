package dev.glyphorder.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings

object SettingsNavigation {
    // OEM font activities can be exported yet protected by signature permissions.
    // Public display settings contains the font entry on the inspected ColorOS ROM.
    fun open(context: Context): Boolean = attempt { action ->
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun attempt(start: (String) -> Unit): Boolean {
        for (action in listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { start(action); return true }
            catch (_: RuntimeException) { /* Permission denial, missing activity or OEM launch failure. */ }
        }
        return false
    }
}

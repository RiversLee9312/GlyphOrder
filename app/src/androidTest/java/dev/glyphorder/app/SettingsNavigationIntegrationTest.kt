package dev.glyphorder.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.ui.SettingsNavigation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationIntegrationTest {
    @Test fun publicDisplaySettingsOpensWithoutAnOemSignaturePermission() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            try {
                scenario.onActivity { assertTrue(SettingsNavigation.open(it)) }
                instrumentation.uiAutomation.waitForIdle(500, 5000)
                assertEquals("com.android.settings", instrumentation.uiAutomation.rootInActiveWindow.packageName.toString())
            } finally {
                // ColorOS can freeze the target while Settings is foreground. Restore
                // the existing activity before ActivityScenario's synchronous cleanup.
                instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                instrumentation.uiAutomation.waitForIdle(200, 5000)
            }
        }
    }
}

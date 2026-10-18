package dev.glyphorder.app

import android.graphics.fonts.SystemFonts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.module.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootVerificationTest {
    /** Only run during deliberate same-font reboot QA. No writes or reboot commands. */
    @Test fun mountedModuleAndSystemFontServiceUseGeneratedFont() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("assertActive") == "true")
        val snapshot = DeviceRepository().inspect()
        assertTrue(snapshot.installed); assertFalse(snapshot.disabled)
        assertTrue(snapshot.currentFamily.startsWith("GlyphOrder-"))
        assertEquals(snapshot.bootId, Root.read("/data/adb/modules/$MODULE_ID/mounted-boot-id"))
        assertTrue(SystemFonts.getAvailableFonts().any { it.file?.name?.startsWith("GlyphOrder-") == true })
        assertTrue(Root.run("dumpsys font").contains("GlyphOrder-"))
    }
}

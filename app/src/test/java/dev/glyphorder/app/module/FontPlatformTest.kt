package dev.glyphorder.app.module

import org.junit.Assert.*
import org.junit.Test

class FontPlatformTest {
    @Test fun lineageTakesPrecedenceOverInheritedVendorProperties() {
        val platform = FontPlatform.identify("24.0", "37")
        assertEquals(FontPlatform.LINEAGE, platform)
        assertFalse(FontPaths.COLOR_OS in platform.configurationPaths)
        assertTrue(FontPaths.MODERN in platform.configurationPaths)
    }
    @Test fun colorOsUsesItsOwnConfigurationAndUnknownRomsDoNot() {
        assertTrue(FontPaths.COLOR_OS in FontPlatform.identify("", "37").configurationPaths)
        assertEquals(FontPlatform.AOSP, FontPlatform.identify("", ""))
        assertFalse(FontPaths.COLOR_OS in FontPlatform.AOSP.configurationPaths)
    }
}

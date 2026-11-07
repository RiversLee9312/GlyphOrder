package dev.glyphorder.app.module

import org.junit.Assert.*
import org.junit.Test

class FontPathsTest {
    @Test fun colorOsBaseTakesPriorityOverTheUnusedAospConfig() {
        val configs = mapOf(FontPaths.LEGACY to "legacy", FontPaths.MODERN to "modern", FontPaths.COLOR_OS to "oem")
        assertEquals(FontPaths.COLOR_OS, FontPaths.active(configs))
        assertEquals(FontPaths.MODERN, FontPaths.active(configs - FontPaths.COLOR_OS))
        assertEquals(FontPaths.LEGACY, FontPaths.active(mapOf(FontPaths.LEGACY to "legacy")))
    }

    @Test fun partitionOverlaysAndFontDirectoriesMatchTheFramework() {
        assertEquals("system/system_ext/etc/fonts_base.xml", FontPaths.overlay(FontPaths.COLOR_OS))
        assertEquals("system/product/etc/fonts_customization.xml", FontPaths.overlay(FontPaths.PRODUCT))
        assertEquals("system/etc/fonts.xml", FontPaths.overlay(FontPaths.LEGACY))
        assertEquals("/system/fonts/", FontPaths.fontDirectory(FontPaths.COLOR_OS))
        assertEquals("/system_ext/fonts/", FontPaths.fontDirectory(FontPaths.COLOR_OS_CUSTOM))
        assertEquals(FontPaths.COLOR_OS_CUSTOM, FontPaths.customization(FontPaths.COLOR_OS))
    }

    @Test fun vendorOnlyFamiliesDoNotCreateFallbackScopesInAospConfigurations() {
        fun family(name: String) = "<familyset><family name=\"$name\"><font>Stock.ttf</font></family></familyset>"
        val configs = mapOf(FontPaths.LEGACY to family("serif"), FontPaths.MODERN to family("sans-serif"),
            FontPaths.COLOR_OS to family("sys-serif"),
            FontPaths.COLOR_OS_CUSTOM to """<fonts-modification><family name="op-sans-en" customizationType="new-named-family"><font>OEM.ttf</font></family></fonts-modification>""")
        assertEquals(setOf("serif"), FontPaths.targets(configs, FontPaths.LEGACY))
        assertEquals(setOf("sans-serif"), FontPaths.targets(configs, FontPaths.MODERN))
        assertEquals(setOf("sys-serif", "op-sans-en"), FontPaths.targets(configs, FontPaths.COLOR_OS))
    }
}

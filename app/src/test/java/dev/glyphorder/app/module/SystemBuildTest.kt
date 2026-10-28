package dev.glyphorder.app.module

import org.junit.Assert.*
import org.junit.Test

class SystemBuildTest {
    private val previous = SystemBuild("retained-vendor-fingerprint", 36, "previous-rom-build")

    @Test fun rejectsAndroidUpgradeWithUnchangedVendorFingerprint() {
        assertFalse(previous.matches(previous.copy(sdk = 37, incremental = "android17-build")))
        assertFalse(previous.matches(previous.copy(sdk = 37)))
    }

    @Test fun rejectsWeeklyRomUpgradeOnTheSameAndroidRelease() {
        assertFalse(previous.matches(previous.copy(incremental = "next-rom-build")))
    }

    @Test fun requiresCompleteEvidenceAndAnExactBuildMatch() {
        assertTrue(previous.matches(previous.copy()))
        assertFalse(previous.copy(sdk = -1, incremental = "").matches(previous))
        assertFalse(SystemBuild("", -1, "").matches(SystemBuild("", -1, "")))
        assertFalse(previous.copy(incremental = "").matches(previous.copy(incremental = "")))
        assertFalse(previous.matches(previous.copy(fingerprint = "different-firmware")))
    }
}

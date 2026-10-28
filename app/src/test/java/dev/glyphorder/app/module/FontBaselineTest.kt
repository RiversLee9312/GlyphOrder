package dev.glyphorder.app.module

import org.junit.Assert.*
import org.junit.Test

class FontBaselineTest {
    private val current = SystemBuild("retained-fingerprint", 37, "new-build")
    private val old = FontBaseline("active", current.copy(sdk = 36, incremental = "old-build"), true, false)
    private val pending = FontBaseline("staged", current, false, false)

    @Test fun ignoresTheDisabledOldBaselineAfterAnUpgradeReboot() {
        assertNull(FontBaseline.select(current, old, null))
        assertNull(FontBaseline.select(current, old.copy(build = SystemBuild(current.fingerprint, -1, "")), null))
    }

    @Test fun acceptsTheNewStagedBaselineAfterInstallationClearsDisable() {
        assertEquals("staged", FontBaseline.select(current, old.copy(disabled = false), pending))
        assertEquals("staged", FontBaseline.select(current, null, pending))
    }

    @Test fun requiresARebootBeforeUsingAnUnverifiedMountedBaseline() {
        assertThrows(IllegalStateException::class.java) {
            FontBaseline.select(current, old.copy(mountedThisBoot = true), pending)
        }
        assertThrows(IllegalStateException::class.java) {
            FontBaseline.select(current, old.copy(disabled = false), null)
        }
    }

    @Test fun rejectsAnOutdatedPendingModuleEvenWithAValidActiveBaseline() {
        val active = old.copy(build = current, disabled = false, mountedThisBoot = true)
        assertThrows(IllegalStateException::class.java) {
            FontBaseline.select(current, active, pending.copy(build = old.build))
        }
        assertEquals("active", FontBaseline.select(current, active, pending))
        assertEquals("active", FontBaseline.select(current, active, pending.copy(build = old.build, disabled = true)))
    }
}

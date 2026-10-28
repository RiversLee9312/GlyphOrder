package dev.glyphorder.app.module

import org.junit.Assert.*
import org.junit.Test

class RootBackendTest {
    @Test fun identifiesTheActiveProviderIncludingVersionSuffixes() {
        assertEquals(RootBackend.MAGISK, RootBackend.fromSuVersion("30.7:MAGISKSU\n"))
        assertEquals(RootBackend.KERNEL_SU, RootBackend.fromSuVersion("3.3.1:KernelSU"))
        assertEquals(RootBackend.KERNEL_SU, RootBackend.fromSuVersion("3.3.1-12345:KernelSU"))
    }

    @Test fun rejectsUnknownOrMisleadingProvidersInsteadOfUsingInstalledFiles() {
        listOf("", "3.3.1", "KernelSU", "30.7:MAGISKSU:extra", "3.3.1:OtherKernelSU", "Permission denied")
            .forEach { assertNull(it, RootBackend.fromSuVersion(it)) }
    }
}

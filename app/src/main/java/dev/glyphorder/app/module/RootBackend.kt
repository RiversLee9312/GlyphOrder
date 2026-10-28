package dev.glyphorder.app.module

/** Identify the active su provider, rather than an old manager's files. */
enum class RootBackend(val label: String) {
    MAGISK("Magisk"), KERNEL_SU("KernelSU");

    companion object {
        fun fromSuVersion(version: String): RootBackend? = when (version.trim().substringAfter(':', "").uppercase()) {
            "MAGISKSU" -> MAGISK
            "KERNELSU" -> KERNEL_SU
            else -> null
        }
    }
}

data class RootManager(val backend: RootBackend, val version: String, val executable: String) {
    val displayName get() = "${backend.label} $version"

    fun installCommand(path: String) = when (backend) {
        RootBackend.MAGISK -> "${Root.quote(executable)} --install-module ${Root.quote(path)}"
        RootBackend.KERNEL_SU -> "${Root.quote(executable)} module install ${Root.quote(path)}"
    }

    companion object {
        fun detect(): RootManager {
            val suVersion = Root.run("su -v").trim()
            val backend = RootBackend.fromSuVersion(suVersion)
                ?: error(dev.glyphorder.app.UiError.key("error_root_command", "unsupported", suVersion))
            val executable = when (backend) {
                RootBackend.MAGISK -> "magisk"
                RootBackend.KERNEL_SU -> listOf("/data/adb/ksud", "/data/adb/ksu/bin/ksud")
                    .firstOrNull { Root.run("[ -x ${Root.quote(it)} ] && echo yes || true").trim() == "yes" }
                    ?: error(dev.glyphorder.app.UiError.key("error_root_command", "missing", "KernelSU: ksud"))
            }
            return RootManager(backend, suVersion.substringBefore(':'), executable)
        }
    }
}

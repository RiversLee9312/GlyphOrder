package dev.glyphorder.app.module

enum class FontPlatform {
    AOSP, LINEAGE, COLOR_OS;

    val configurationPaths get() = if (this == COLOR_OS) FontPaths.all else FontPaths.all - setOf(FontPaths.COLOR_OS, FontPaths.COLOR_OS_CUSTOM)

    companion object {
        fun identify(lineageVersion: String, oplusApi: String): FontPlatform = when {
            lineageVersion.isNotBlank() -> LINEAGE
            (oplusApi.toIntOrNull() ?: 0) > 0 -> COLOR_OS
            else -> AOSP
        }
    }
}

package dev.glyphorder.app.module

/** ColorOS redirects SystemFonts to its own configuration, even on Android 15+. */
object FontPaths {
    const val LEGACY = "/system/etc/fonts.xml"
    const val MODERN = "/system/etc/font_fallback.xml"
    const val PRODUCT = "/product/etc/fonts_customization.xml"
    const val COLOR_OS = "/system_ext/etc/fonts_base.xml"
    const val COLOR_OS_CUSTOM = "/system_ext/etc/fonts_customization.xml"
    val all = listOf(MODERN, LEGACY, PRODUCT, COLOR_OS, COLOR_OS_CUSTOM)
    val bases = listOf(LEGACY, MODERN, COLOR_OS)

    fun active(configs: Map<String, String>): String =
        listOf(COLOR_OS, MODERN, LEGACY).first { it in configs }

    fun customization(path: String) = if (path == COLOR_OS) COLOR_OS_CUSTOM else PRODUCT
    fun targets(configs: Map<String, String>, path: String): Set<String> {
        val related = when (path) {
            COLOR_OS, COLOR_OS_CUSTOM -> listOf(COLOR_OS, COLOR_OS_CUSTOM)
            PRODUCT -> listOf(LEGACY, MODERN, PRODUCT)
            else -> listOf(path, customization(path))
        }
        return related.flatMap { configs[it]?.let(FontConfig::families).orEmpty() }.toSet()
    }
    fun isCustomization(path: String) = path == PRODUCT || path == COLOR_OS_CUSTOM
    fun fontDirectory(path: String) = when (path) {
        PRODUCT -> "/product/fonts/"
        COLOR_OS_CUSTOM -> "/system_ext/fonts/"
        else -> "/system/fonts/"
    }
    fun overlay(path: String) = if (path.startsWith("/product/") || path.startsWith("/system_ext/"))
        "system$path" else path.removePrefix("/")
}

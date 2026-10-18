package dev.glyphorder.app.module

import dev.glyphorder.app.UiError
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile

/** Conservative upper bounds: count even fonts that this boot might fail to load. */
object ModuleSafety {
    // Minikin stores family indices in a byte and aborts the process above this limit.
    const val MAX_FAMILIES = 254
    private const val MAX_XML_BYTES = 16 * 1024 * 1024
    private const val LEGACY = "/system/etc/fonts.xml"
    private const val MODERN = "/system/etc/font_fallback.xml"
    private const val PRODUCT = "/product/etc/fonts_customization.xml"
    private val paths = setOf(LEGACY, MODERN, PRODUCT)

    private fun children(parent: Element, tag: String? = null): List<Element> =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }
            .filterIsInstance<Element>().filter { tag == null || it.tagName == tag }

    private data class Named(val families: List<Element>, val fallback: String)

    /** Both XML dialects must be safe, including product overrides and named lists. */
    fun validate(configs: Map<String, String>): Int {
        var maximum = 0
        require(LEGACY in configs && paths.containsAll(configs.keys)) { UiError.key("error_font_config_format") }
        val product = configs[PRODUCT]?.let { FontConfig.parse(it).documentElement }
        require(product == null || product.tagName == "fonts-modification") { UiError.key("error_font_config_format") }
        listOf(LEGACY, MODERN).forEach { path ->
            val xml = configs[path] ?: return@forEach
            val root = FontConfig.parse(xml).documentElement
            require(root.tagName == "familyset") { UiError.key("error_font_config_format") }
            val named = linkedMapOf<String, Named>()
            val fallback = mutableListOf<Element>()
            var first = true
            fun addNamed(element: Element): Boolean {
                val name = element.getAttribute("name")
                val families = if (element.tagName == "family-list") children(element, "family") else listOf(element)
                require(name.isNotBlank()) { UiError.key("error_font_config_format") }
                val populated = families.filter { children(it, "font").isNotEmpty() }
                if (populated.isEmpty()) return false
                named[name] = Named(populated, element.getAttribute("fallback"))
                return true
            }
            children(root).forEach { element ->
                when (element.tagName) {
                    "family", "family-list" -> {
                        if (element.getAttribute("ignore") in setOf("true", "1")) return@forEach
                        if (element.hasAttribute("name")) {
                            if (!addNamed(element)) return@forEach
                            // AOSP also pushes the first named family's actual nodes into
                            // every default fallback list. Its own list sees those only once.
                            if (first) fallback += named.getValue(element.getAttribute("name")).families
                        } else {
                            require(element.tagName == "family") { UiError.key("error_font_config_format") }
                            if (children(element, "font").isEmpty()) return@forEach
                            fallback += element
                        }
                        first = false
                    }
                }
            }
            product?.let { customization ->
                children(customization).forEach { element ->
                    when (element.tagName) {
                        "family", "family-list" -> {
                            when (element.getAttribute("customizationType")) {
                                "new-named-family" -> addNamed(element) // Product names override system names.
                                "new-locale-family" -> {
                                    // Upper bound for prepend/append/replace customizations:
                                    // retain the originals and count the customization as well.
                                    require(element.tagName == "family") { UiError.key("error_font_config_format") }
                                    fallback += element
                                }
                                else -> throw IllegalArgumentException(UiError.key("error_font_config_format"))
                            }
                        }
                    }
                }
            }
            require(named.isNotEmpty()) { UiError.key("error_font_config_format") }
            fun defaultCount(name: String, chain: Named): Int {
                val eligible = fallback.filter { family ->
                    children(family, "font").any { font ->
                        !font.hasAttribute("fallbackFor") || font.getAttribute("fallbackFor") == name
                    }
                }
                // Identity matters: distinct XML families with identical files still
                // produce distinct native families. lang/variant/weights do not partition them.
                return (chain.families + eligible).distinct().size
            }
            named.forEach { (name, chain) ->
                val count = if (chain.fallback.isEmpty()) defaultCount(name, chain) else {
                    val target = named[chain.fallback]
                    require(target != null && target.fallback.isEmpty()) { UiError.key("error_font_config_format") }
                    chain.families.size + defaultCount(chain.fallback, target)
                }
                require(count <= MAX_FAMILIES) {
                    UiError.key("error_font_family_limit", path, name, count, MAX_FAMILIES)
                }
                maximum = maxOf(maximum, count)
            }
        }
        return maximum
    }

    /** Re-read the bytes being installed; old/generated ZIPs must pass the same gate. */
    fun validateArchive(file: File, expectedConfigs: Set<String>) {
        require(LEGACY in expectedConfigs && paths.containsAll(expectedConfigs)) { UiError.key("error_module_integrity") }
        val configs = ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            paths.mapNotNull { path ->
                val overlay = if (path.startsWith("/product/")) "system$path" else path.removePrefix("/")
                val matching = entries.filter { it.name == overlay }
                require(matching.size == if (path in expectedConfigs) 1 else 0) { UiError.key("error_module_integrity") }
                matching.singleOrNull()?.let { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_XML_BYTES + 1) }
                    require(bytes.size <= MAX_XML_BYTES) { UiError.key("error_module_integrity") }
                    path to bytes.toString(Charsets.UTF_8)
                }
            }.toMap()
        }
        validate(configs)
    }
}

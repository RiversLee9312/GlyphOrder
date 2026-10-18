package dev.glyphorder.app.module

import dev.glyphorder.app.font.FontInfo
import dev.glyphorder.app.font.FontPlan
import dev.glyphorder.app.font.FontSelection
import dev.glyphorder.app.font.FontStyles
import dev.glyphorder.app.font.CmapCoverage
import dev.glyphorder.app.UiError
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

object FontConfig {
    val languages = setOf("zh-Hans", "zh-Hant", "ja", "ko")
    fun parse(xml: String): Document {
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { UiError.key("error_xml_declaration") }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
    fun families(xml: String): List<String> = children(parse(xml).documentElement).filter { it.tagName in setOf("family", "family-list") }
        .map { it.getAttribute("name") }.filter { it.isNotBlank() }
    fun files(xml: String): Set<String> = parse(xml).getElementsByTagName("font").let { nodes ->
        (0 until nodes.length).map { node ->
            val f = nodes.item(node)
            (0 until f.childNodes.length).map { f.childNodes.item(it) }
                .filter { it.nodeType == org.w3c.dom.Node.TEXT_NODE }.joinToString("") { it.textContent }.trim()
        }.toSet()
    }
    private fun elements(parent: Element, tag: String) = (0 until parent.childNodes.length)
        .map { parent.childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
    private fun children(parent: Element) = (0 until parent.childNodes.length)
        .map { parent.childNodes.item(it) }.filterIsInstance<Element>()
    // Android's DOM throws UnsupportedOperationException for compareDocumentPosition.
    private fun precedes(left: org.w3c.dom.Node, right: org.w3c.dom.Node): Boolean {
        var next = left.nextSibling
        while (next != null) {
            if (next === right) return true
            next = next.nextSibling
        }
        return false
    }

    fun patch(xml: String, modern: Boolean, plan: FontPlan, fonts: List<FontInfo>, product: Boolean = false,
              systemCoverage: Map<String, List<IntRange>> = emptyMap(), knownTargets: Set<String> = emptySet()): String {
        if (plan.styleMode) {
            val assignments = FontStyles.assignments(plan, plan.targets)
            require(assignments.isNotEmpty()) { UiError.key("error_select_existing_family") }
            // Disjoint fallbackFor scopes keep each style's primary and supplements isolated.
            return assignments.entries.groupBy({ it.value }, { it.key }).entries.fold(xml) { current, (id, names) ->
                patchSingle(current, modern, plan.copy(primary = id, targets = names.toSet(), global = false, styleMode = false),
                    fonts, product, systemCoverage, knownTargets, isolate = true)
            }
        }
        return patchSingle(xml, modern, plan, fonts, product, systemCoverage, knownTargets)
    }

    private fun patchSingle(xml: String, modern: Boolean, plan: FontPlan, fonts: List<FontInfo>, product: Boolean,
                            systemCoverage: Map<String, List<IntRange>>, knownTargets: Set<String>, isolate: Boolean = false): String {
        val doc = parse(xml)
        val root = doc.documentElement
        require(root.tagName == if (product) "fonts-modification" else "familyset") { UiError.key("error_font_config_format") }
        val primary = fonts.find { it.id == plan.primary }
        val emojiNames = children(root).filter { element ->
            element.getElementsByTagName("font").let { nodes ->
                (0 until nodes.length).any { nodes.item(it).textContent.contains("NotoColorEmoji") }
            }
        }.map { it.getAttribute("name") }.toSet()
        val targetNames = (if (plan.global) plan.targets + families(xml) else plan.targets) - emojiNames
        val allFamilies = elements(root, "family")
        val emojiBefore = allFamilies.filter { family -> elements(family, "font").any { it.textContent.contains("NotoColorEmoji") } }
            .map(::serialize)
        fun appendFamily(family: Element, font: FontInfo, scoped: Boolean = false) {
            val targets = if (scoped) targetNames.toList() else listOf(null)
            targets.forEach { target ->
                FontSelection.family(font, fonts).forEach { face ->
                    appendFont(doc, family, face, modern, plan.axes[font.id].orEmpty(), target)
                }
            }
        }
        primary?.let { font ->
            val named = allFamilies.filter { it.getAttribute("name") in targetNames }.toMutableList()
            elements(root, "family-list").filter { it.getAttribute("name") in targetNames }.forEach { list ->
                val members = elements(list, "family")
                members.firstOrNull()?.let(named::add)
                members.drop(1).forEach(list::removeChild)
            }
            named.forEach { family ->
                elements(family, "font").forEach(family::removeChild)
                appendFamily(family, font)
            }
        }
        val primaryCoverage = primary?.let { CmapCoverage.intersection(FontSelection.family(it, fonts).map(FontInfo::coverage)) }.orEmpty()
        data class OriginalCoverage(val guaranteed: List<IntRange>, val possible: List<IntRange>, val complete: Boolean)
        val coverageCache = mutableMapOf<Pair<Element, String?>, OriginalCoverage>()
        fun originalCoverage(family: Element, target: String): OriginalCoverage {
            val nodes = elements(family, "font")
            val specific = nodes.filter { it.getAttribute("fallbackFor") == target }
            val scope = target.takeIf { specific.isNotEmpty() }
            return coverageCache.getOrPut(family to scope) {
                val selected = if (scope == null) nodes.filter { !it.hasAttribute("fallbackFor") } else specific
                val coverage = selected.map { node ->
                    val name = node.textContent.trim()
                    val filename = if (name.startsWith('/')) name else "/system/fonts/$name"
                    systemCoverage["$filename#${node.getAttribute("index").ifEmpty { "0" }}"]
                }
                OriginalCoverage(CmapCoverage.intersection(coverage.map { it.orEmpty() }),
                    CmapCoverage.union(coverage.map { it.orEmpty() }), selected.isNotEmpty() && coverage.all { it != null })
            }
        }
        val originals = allFamilies.filter { !it.hasAttribute("name") && it.getAttribute("ignore") !in setOf("true", "1") }
        // A covered supplement can use the existing system fonts. Prove this for
        // EVERY selected chain, using the intersection of all weights in each family.
        val available = targetNames.associateWith { target ->
            CmapCoverage.union(listOf(primaryCoverage) + originals.map { originalCoverage(it, target).guaranteed })
        }
        val automatic = FontSelection.routes(plan, fonts).mapValues { (_, faces) -> faces.filterNot { font ->
            font.fallbackGroup !in setOf("noto-serif", "misans-global") &&
                font.coverage.isNotEmpty() && available.isNotEmpty() && available.values.all { CmapCoverage.contains(it, font.coverage) }
        } }
        data class Route(val lang: String, val variant: String, val before: Element?, val fonts: List<Pair<FontInfo, Boolean>>)
        val routes = mutableListOf<Route>()
        val globalLanguages = if ((plan.global || isolate) && primary != null) originals.filterNot { family ->
            elements(family, "font").any { it.textContent.contains("NotoColorEmoji") }
        }
            .flatMap { it.getAttribute("lang").split(',') }.filter { it.isNotBlank() && it != "und-Zsye" }.toSet() else emptySet()
        if (!product) (plan.fallbacks.keys + automatic.keys + globalLanguages).forEach { lang ->
            require(lang in languages || lang in globalLanguages || lang.matches(Regex("und-[A-Z][a-z]{3}"))) { UiError.key("error_language_script_unsupported") }
            val manual = plan.fallbacks[lang]?.let { id ->
                fonts.find { it.id == id } ?: error(UiError.key("error_fallback_font_removed"))
            }
            // Every primary uses the same automatic pool: primary first, followed
            // by an explicit script selection and relevant Noto supplements.
            val preferred = (listOfNotNull(primary.takeIf { automatic.isNotEmpty() || plan.global || isolate }, manual) + automatic[lang].orEmpty()).distinctBy { it.id }
            // Language tags rank families; they do not isolate them into separate native
            // collections. Repeating a face for each script consumes the same 254 slots.
            val matching = allFamilies.filter { lang in it.getAttribute("lang").split(',').map(String::trim) }
            // A compact/elegant system family can outrank an unqualified insertion.
            // Match each existing variant so the user's font is actually preferred.
            val variants = matching.map { it.getAttribute("variant") }.distinct().ifEmpty { listOf("") }
            variants.forEach { variant ->
                val first = matching.firstOrNull { it.getAttribute("variant") == variant }
                    ?: allFamilies.firstOrNull { !it.hasAttribute("name") }
                routes += Route(lang, variant, first, preferred.map { it to (isolate || primary != null) })
            }
        }
        val reused = mutableMapOf<Element, String>()
        val reusedCoverage = mutableMapOf<Element, List<IntRange>>()
        val retainedCoverage = mutableMapOf<Element, Map<String, List<IntRange>>>()
        fun coverageOutside(excluded: Element): Map<String, List<IntRange>> = retainedCoverage.getOrPut(excluded) {
            // Use CURRENT guarantees, so each replacement preserves the union
            // inductively. A later replacement cannot rely on a removed face.
            val chains = targetNames.associateWith { target ->
                originals.filter { it !== excluded }.map { reusedCoverage[it] ?: originalCoverage(it, target).guaranteed }
            }
            val unions = chains.values.distinct().associateWith(CmapCoverage::union)
            chains.mapValues { unions.getValue(it.value) }
        }
        val shared = mutableMapOf<String, MutableList<Element>>()
        // Process earlier anchors first, so a shared family stays ahead of every
        // original it supersedes. Never merge across a route's ordering constraint.
        routes.sortedBy { allFamilies.indexOf(it.before).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }.forEach { route ->
            var previous: Element? = null
            route.fonts.forEach face@ { (font, scoped) ->
                val template = doc.createElement("family").apply {
                    if (route.variant.isNotBlank()) setAttribute("variant", route.variant)
                }
                appendFamily(template, font, scoped)
                val key = serialize(template) // Includes face group, axes, variant and fallbackFor.
                val candidates = shared.getOrPut(key) { mutableListOf() }
                val existing = candidates.firstOrNull { candidate ->
                    previous == null || previous === candidate || precedes(previous!!, candidate)
                }
                // Reuse an already ordered injected family before allocating another
                // original slot. A later reused primary otherwise forces copies of
                // every supplement that was already inserted ahead of that slot.
                if (existing != null) {
                    existing.setAttribute("lang", (existing.getAttribute("lang").split(',').filter(String::isNotBlank) + route.lang).distinct().joinToString(","))
                    previous = existing
                    return@face
                }
                val fontCoverage = CmapCoverage.intersection(FontSelection.family(font, fonts).map(FontInfo::coverage))
                val replacementCoverage = CmapCoverage.union(listOf(primaryCoverage, fontCoverage))
                // Reuse a script's original slot only when all its previous glyphs
                // remain available through this replacement and the current chain.
                // Other targets keep their original default fonts in the same node.
                val slot = if (scoped && (font.fallbackGroup != null || font.id == primary?.id) && fontCoverage.isNotEmpty()) originals.firstOrNull { family ->
                    route.lang in family.getAttribute("lang").split(',').map(String::trim) &&
                        family.getAttribute("variant") == route.variant &&
                        (previous == null || previous === family || precedes(previous!!, family)) &&
                        elements(family, "font").none { it.textContent.contains("NotoColorEmoji") } &&
                        (reused[family] == key || (family !in reused && targetNames.all { target ->
                            val old = originalCoverage(family, target)
                            old.complete && (CmapCoverage.contains(replacementCoverage, old.possible) ||
                                CmapCoverage.contains(CmapCoverage.union(listOf(replacementCoverage,
                                    coverageOutside(family).getValue(target))), old.possible))
                        }))
                } else null
                if (slot != null) {
                    if (slot !in reused) {
                        elements(slot, "font").filter { it.getAttribute("fallbackFor") in targetNames }.forEach(slot::removeChild)
                        elements(template, "font").forEach { slot.appendChild(it) }
                        reused[slot] = key
                        reusedCoverage[slot] = fontCoverage
                        retainedCoverage.clear()
                    }
                    // Preserve ordering: later supplements must follow this reused slot.
                    previous = slot
                    return@face
                }
                val family = template.also {
                    val followsAnchor = previous != null && route.before != null &&
                        (previous === route.before || precedes(route.before, previous!!))
                    val anchor = if (followsAnchor) previous!!.nextSibling else route.before
                    if (anchor != null) root.insertBefore(it, anchor) else root.appendChild(it)
                    candidates += it
                }
                family.setAttribute("lang", (family.getAttribute("lang").split(',').filter(String::isNotBlank) + route.lang).distinct().joinToString(","))
                previous = family
            }
        }
        if (!product) {
            // Universal fonts fill missing glyphs, after all device fallback families, preserving emoji priority.
            // Tagged automatic faces already participate in fallback for all characters.
            // Appending them again as unqualified families doubles the native chain.
            val unqualified = plan.universal.distinct().map { id ->
                val font = fonts.find { it.id == id } ?: error(UiError.key("error_universal_font_removed"))
                font
            } + FontSelection.dependencies(plan, fonts).filter { it.fallbackTags.isEmpty() }
            unqualified.distinctBy { FontSelection.family(it, fonts).map(FontInfo::id) to plan.axes[it.id].orEmpty() }.forEach { font ->
                val family = doc.createElement("family")
                appendFamily(family, font, isolate || primary != null || font in FontSelection.dependencies(plan, fonts))
                root.appendChild(family)
            }
        }
        val emojiAfter = elements(root, "family").filter { family -> elements(family, "font").any { it.textContent.contains("NotoColorEmoji") } }
            .map(::serialize)
        check(emojiBefore == emojiAfter) { UiError.key("error_emoji_config_validation") }
        if (!product && primary != null && systemCoverage.isNotEmpty()) {
            retireRedundantFallbacks(root, targetNames, knownTargets + families(xml) + plan.targets, fonts, systemCoverage, primaryCoverage)
        }
        return serialize(doc)
    }

    /** Retire only a selected chain's redundant original slots, preserving other named
     * chains with explicit fallbackFor copies. Each proof uses the CURRENT remaining
     * guarantees, never the initial union or a font that was already retired. */
    private fun retireRedundantFallbacks(root: Element, targets: Set<String>, allTargets: Set<String>,
                                        fonts: List<FontInfo>, systemCoverage: Map<String, List<IntRange>>,
                                        primaryCoverage: List<IntRange>) {
        val custom = fonts.associate { "GlyphOrder-${it.fileName}" to it.coverage }
        data class Coverage(val id: Int, val guaranteed: List<IntRange>, val possible: List<IntRange>, val complete: Boolean, val custom: Boolean)
        val byFaces = mutableMapOf<List<String>, Coverage>()
        val byId = mutableMapOf<Int, Coverage>()
        val empty = Coverage(0, emptyList(), emptyList(), false, false)
        fun coverage(family: Element, target: String): Coverage {
            val nodes = elements(family, "font")
            val specific = nodes.filter { it.getAttribute("fallbackFor") == target }
            val faces = specific.ifEmpty { nodes.filter { !it.hasAttribute("fallbackFor") } }
            val keys = faces.map { "${it.textContent.trim()}#${it.getAttribute("index").ifEmpty { "0" }}" }.distinct().sorted()
            return byFaces.getOrPut(keys) {
            val values = faces.map { node ->
                val name = node.textContent.trim()
                custom[name] ?: systemCoverage["${if (name.startsWith('/')) name else "/system/fonts/$name"}#${node.getAttribute("index").ifEmpty { "0" }}"]
            }
            Coverage(byId.size + 1, CmapCoverage.intersection(values.map { it.orEmpty() }), CmapCoverage.union(values.map { it.orEmpty() }),
                faces.isNotEmpty() && values.all { it != null && it.isNotEmpty() }, faces.any { it.textContent.trim() in custom })
                .also { byId[it.id] = it }
            }
        }
        val families = elements(root, "family").filter { !it.hasAttribute("name") && it.getAttribute("ignore") !in setOf("true", "1") }
        val contributions = families.associateWith { family -> targets.associateWith { coverage(family, it) }.toMutableMap() }.toMutableMap()
        families.forEach { family ->
            if (elements(family, "font").any { it.textContent.contains("NotoColorEmoji") }) return@forEach
            val eligible = targets.filter { target -> contributions.getValue(family).getValue(target).let { it.complete && !it.custom } }
            val chains = eligible.associateWith { target ->
                families.filter { it !== family }.map { contributions.getValue(it).getValue(target).id }.filter { it != 0 }.distinct().sorted()
            }
            val unions = chains.values.distinct().associateWith { ids ->
                CmapCoverage.union(listOf(primaryCoverage) + ids.map { byId.getValue(it).guaranteed })
            }
            val retired = eligible.filter { target -> CmapCoverage.contains(unions.getValue(chains.getValue(target)),
                contributions.getValue(family).getValue(target).possible) }.toSet()
            if (retired.isEmpty()) return@forEach
            val nodes = elements(family, "font")
            val defaults = nodes.filter { !it.hasAttribute("fallbackFor") }
            val explicit = nodes.map { it.getAttribute("fallbackFor") }.filter(String::isNotBlank).toSet()
            // Named product chains must be in allTargets as well as system names.
            (allTargets - retired - explicit).forEach { target -> defaults.forEach { node ->
                family.appendChild(node.cloneNode(true).also { (it as Element).setAttribute("fallbackFor", target) })
            } }
            defaults.forEach(family::removeChild)
            nodes.filter { it.getAttribute("fallbackFor") in retired }.forEach(family::removeChild)
            retired.forEach { contributions.getValue(family)[it] = empty }
            if (elements(family, "font").isEmpty()) root.removeChild(family)
        }
    }

    private fun appendFont(doc: Document, family: Element, info: FontInfo, modern: Boolean,
                           values: Map<String, Float>, fallbackFor: String? = null) {
        val dynamicAxes = info.axes.filter { it.tag == "wght" || it.tag == "ital" }.map { it.tag }
        fun add(weight: Int, italic: Boolean, dynamic: Boolean) {
            val element = doc.createElement("font")
            if (dynamic) element.setAttribute("supportedAxes", dynamicAxes.joinToString(","))
            element.setAttribute("weight", weight.toString())
            element.setAttribute("style", if (italic) "italic" else "normal")
            if (fallbackFor != null) element.setAttribute("fallbackFor", fallbackFor)
            element.appendChild(doc.createTextNode("GlyphOrder-${info.fileName}"))
            info.axes.forEach { axis ->
                // Seed the default instance explicitly. Rubik's upstream fvar/OS2 default
                // is Light (300); supportedAxes alone leaves that initial outline in use.
                val value = when (axis.tag) {
                    "wght" -> weight.toFloat()
                    "ital" -> if (italic) 1f else 0f
                    else -> values[axis.tag] ?: axis.default
                }.coerceIn(axis.min, axis.max)
                element.appendChild(doc.createElement("axis").apply {
                    setAttribute("tag", axis.tag); setAttribute("stylevalue", value.toString())
                })
            }
            family.appendChild(element)
        }
        if (modern && dynamicAxes.isNotEmpty()) add(if ("wght" in dynamicAxes) 400 else info.weight, info.italic, true)
        else if (info.axes.any { it.tag == "wght" }) {
            (listOf(400) + (100..900 step 100).filter { it != 400 }).forEach { weight ->
                add(weight, info.italic, false)
                if (info.axes.any { it.tag == "ital" }) add(weight, true, false)
            }
        } else add(info.weight, info.italic, false)
    }
    fun serialize(node: org.w3c.dom.Node): String = StringWriter().also { writer ->
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "utf-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (node is Document) "no" else "yes")
        }.transform(DOMSource(node), StreamResult(writer))
    }.toString()
}

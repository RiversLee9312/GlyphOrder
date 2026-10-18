package dev.glyphorder.app.module

import dev.glyphorder.app.font.*
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class FontConfigTest {
    private val xml = """<familyset version="23"><!--keep--><family name="sans-serif"><font weight="400">Roboto.ttf</font></family><family name="serif"><font>Serif.ttf</font></family><family lang="und-Arab"><font>Arabic.ttf</font></family><family lang="zh-Hans"><font index="2">CJK.ttc</font><font fallbackFor="serif">SerifCJK.ttc</font></family><family lang="und-Zsye"><font>NotoColorEmoji.ttf</font></family><alias name="sans" to="sans-serif"/></familyset>"""
    private val static = FontInfo("a", "abc.ttf", "Test", "Test-Regular", 400, false, emptyList(), 100, setOf("拉丁"))
    private val variable = static.copy(id = "v", fileName = "var.otf", axes = listOf(FontAxis("wght", 200f, 400f, 800f), FontAxis("wdth", 75f, 100f, 125f)))
    private fun fontNodes(text: String) = FontConfig.parse(text).getElementsByTagName("font").let { n -> (0 until n.length).map { n.item(it) as Element } }

    @Test fun staticDoesNotReceiveVariationAxes() {
        val result = FontConfig.patch(xml, true, FontPlan(primary = "a", axes = mapOf("a" to mapOf("wght" to 800f))), listOf(static))
        val f = fontNodes(result).first()
        assertFalse(f.hasAttribute("supportedAxes")); assertEquals(0, f.getElementsByTagName("axis").length)
        assertTrue(result.contains("GlyphOrder-abc.ttf")); assertTrue(result.contains("<!--keep-->"))
        assertTrue(result.contains("name=\"sans\"")); assertTrue(result.contains("Serif.ttf"))
    }
    @Test fun modernUsesOnlySupportedRuntimeAxes() {
        val result = FontConfig.patch(xml, true, FontPlan(primary = "v", axes = mapOf("v" to mapOf("wdth" to 1000f))), listOf(variable))
        val f = fontNodes(result).first()
        assertEquals("wght", f.getAttribute("supportedAxes"))
        val axes = f.getElementsByTagName("axis")
        val axis = (0 until axes.length).map { axes.item(it) as Element }.single { it.getAttribute("tag") == "wdth" }
        assertEquals("wdth", axis.getAttribute("tag")); assertEquals("125.0", axis.getAttribute("stylevalue"))
        assertEquals("400", f.getAttribute("weight"))
    }
    @Test fun legacyEmitsExplicitClampedInstances() {
        val result = FontConfig.patch(xml, false, FontPlan(primary = "v"), listOf(variable))
        assertFalse(result.contains("supportedAxes"))
        val nodes = fontNodes(result).filter { it.textContent.contains("GlyphOrder-") }
        assertEquals(9, nodes.size)
        val weights = nodes.map { f -> (f.getElementsByTagName("axis").item(0) as Element).getAttribute("stylevalue").toFloat() }
        assertEquals(400f, weights.first()); assertEquals(200f, weights.min()); assertEquals(800f, weights.max())
    }

    @Test fun rubikLightMetadataNeverBecomesTheRegularDefault() {
        val rubik = variable.copy(weight = 300, axes = listOf(FontAxis("wght", 300f, 300f, 900f)))
        listOf(true, false).forEach { modern ->
            val result = FontConfig.patch(xml, modern, FontPlan(primary = rubik.id), listOf(rubik))
            val regular = fontNodes(result).first()
            assertEquals("400", regular.getAttribute("weight"))
            val axis = regular.getElementsByTagName("axis").item(0) as Element
            assertEquals("wght", axis.getAttribute("tag"))
            assertEquals("400.0", axis.getAttribute("stylevalue"))
        }
    }

    @Test fun mixedStylesKeepNamedFamiliesAndScriptRoutesIsolated() {
        val primary = static.copy(coverage = listOf(65..90))
        val serif = variable.copy(preset = "noto-serif", coverage = listOf(65..90))
        val arabic = static.copy(id = "serif-arabic", fileName = "serif-arabic.ttf", hidden = true,
            fallbackGroup = "noto-serif", fallbackTags = setOf("und-Arab"), coverage = listOf(0x600..0x6ff))
        val plan = FontSelection.forDevice(FontPlan(styleMode = true, styleFonts = mapOf("sans" to primary.id, "serif" to serif.id)), listOf("sans-serif", "serif"))
        val result = FontConfig.patch(xml, true, plan, listOf(primary, serif, arabic))
        val nodes = fontNodes(result)
        assertTrue(nodes.single { (it.parentNode as Element).getAttribute("name") == "sans-serif" }.textContent.contains("abc.ttf"))
        assertTrue(nodes.single { (it.parentNode as Element).getAttribute("name") == "serif" }.textContent.contains("var.otf"))
        val supplements = nodes.filter { it.textContent.contains("serif-arabic.ttf") }
        assertTrue(supplements.isNotEmpty())
        assertTrue(supplements.all { it.getAttribute("fallbackFor") == "serif" })
        assertTrue(nodes.filter { it.textContent.contains("GlyphOrder-") && !(it.parentNode as Element).hasAttribute("name") }
            .all { it.getAttribute("fallbackFor") in setOf("sans-serif", "serif") })
    }

    @Test fun mixedModePreservesUnselectedFamiliesAndScopesManualAndUniversalFonts() {
        val plan = FontSelection.forDevice(FontPlan(primary = "v", styleMode = true, styleFonts = mapOf("serif" to "a"),
            fallbacks = mapOf("und-Arab" to "v"), universal = listOf("v")), listOf("sans-serif", "serif"))
        val result = FontConfig.patch(xml, true, plan, listOf(static, variable))
        assertTrue(result.contains("Roboto.ttf")); assertFalse(result.contains("Serif.ttf"))
        val scoped = fontNodes(result).filter { it.textContent.contains("GlyphOrder-var.otf") }
        assertTrue(scoped.isNotEmpty()); assertTrue(scoped.all { it.getAttribute("fallbackFor") == "serif" })
    }

    @Test fun casualAndCursiveReceiveSeparateFontsWhileUnselectedSansRemainsStock() {
        val input = xml.replace("</familyset>", "<family name=\"casual\"><font>Casual.ttf</font></family>" +
            "<family name=\"cursive\"><font>Cursive.ttf</font></family></familyset>")
        val plan = FontSelection.forDevice(FontPlan(styleMode = true, styleFonts = mapOf("casual" to "a", "cursive" to "v")),
            listOf("sans-serif", "serif", "casual", "cursive"))
        val result = FontConfig.patch(input, true, plan, listOf(static, variable))
        val nodes = fontNodes(result)
        assertTrue(nodes.single { (it.parentNode as Element).getAttribute("name") == "casual" }.textContent.contains("abc.ttf"))
        assertTrue(nodes.single { (it.parentNode as Element).getAttribute("name") == "cursive" }.textContent.contains("var.otf"))
        assertTrue(result.contains("Roboto.ttf")); assertTrue(result.contains("Serif.ttf"))
        val cleared = FontConfig.patch(input, true, plan.copy(styleFonts = mapOf("cursive" to "v")), listOf(static, variable))
        assertTrue(cleared.contains("Casual.ttf")); assertFalse(cleared.contains("Cursive.ttf"))
    }

    @Test fun retiresRedundantSlotsPerTargetWithoutCircularProofsOrChangingOtherChains() {
        val input = "<familyset><family name=\"sans-serif\"><font>Roboto.ttf</font></family>" +
            "<family name=\"serif\"><font>Serif.ttf</font></family>" +
            "<family lang=\"und-Latn\"><font>A.ttf</font></family>" +
            "<family lang=\"und-Latn\"><font>B.ttf</font></family></familyset>"
        val primary = static.copy(coverage = listOf(65..66))
        val result = FontConfig.patch(input, true, FontPlan(primary = primary.id, automaticFallback = false), listOf(primary),
            systemCoverage = mapOf("/system/fonts/A.ttf#0" to listOf(67..70), "/system/fonts/B.ttf#0" to listOf(67..70)),
            knownTargets = setOf("sans-serif", "serif", "product-clock"))
        val a = fontNodes(result).filter { it.textContent.trim() == "A.ttf" }
        assertEquals(setOf("serif", "product-clock"), a.map { it.getAttribute("fallbackFor") }.toSet())
        val b = fontNodes(result).single { it.textContent.trim() == "B.ttf" }
        assertFalse(b.hasAttribute("fallbackFor")) // A no longer proves sans-serif coverage for B.
    }
    @Test fun cjkAndNonCjkScriptsPrecedeOriginalFallback() {
        val plan = FontPlan(fallbacks = linkedMapOf("zh-Hans" to "a", "und-Arab" to "v", "und-Osge" to "a"))
        val result = FontConfig.patch(xml, true, plan, listOf(static, variable))
        assertTrue(result.indexOf("GlyphOrder-var.otf") < result.indexOf("Arabic.ttf"))
        assertTrue(result.indexOf("GlyphOrder-abc.ttf") < result.indexOf("CJK.ttc"))
        assertTrue(result.contains("SerifCJK.ttc")); assertTrue(result.contains("NotoColorEmoji.ttf"))
    }
    @Test fun universalPreservesOrderAfterSystemEmoji() {
        val result = FontConfig.patch(xml, true, FontPlan(universal = listOf("v", "a")), listOf(static, variable))
        assertTrue(result.indexOf("NotoColorEmoji.ttf") < result.indexOf("GlyphOrder-var.otf"))
        assertTrue(result.indexOf("GlyphOrder-var.otf") < result.indexOf("GlyphOrder-abc.ttf"))
    }
    @Test fun scriptFallbackMatchesCompactAndElegantVariants() {
        val input = """<familyset><family name="sans-serif"><font>Roboto.ttf</font></family><family lang="und-Arab" variant="elegant"><font>ArabicElegant.ttf</font></family><family lang="und-Arab" variant="compact"><font>ArabicCompact.ttf</font></family></familyset>"""
        val result = FontConfig.patch(input, true, FontPlan(fallbacks = mapOf("und-Arab" to "a")), listOf(static))
        val families = FontConfig.parse(result).getElementsByTagName("family").let { n -> (0 until n.length).map { n.item(it) as Element } }
        assertEquals(5, families.size)
        assertEquals("elegant", families[1].getAttribute("variant")); assertTrue(families[1].textContent.contains("GlyphOrder-"))
        assertEquals("compact", families[3].getAttribute("variant")); assertTrue(families[3].textContent.contains("GlyphOrder-"))
        assertTrue(result.contains("ArabicElegant.ttf")); assertTrue(result.contains("ArabicCompact.ttf"))
    }
    @Test fun productFamiliesUseLegacySyntaxAndKeepCustomizationType() {
        val product = """<fonts-modification version="1"><family customizationType="new-named-family" name="google-sans"><font>Google.ttf</font></family></fonts-modification>"""
        val result = FontConfig.patch(product, false, FontPlan(primary = "v", targets = setOf("google-sans")), listOf(variable), true)
        assertTrue(result.contains("customizationType=\"new-named-family\"")); assertFalse(result.contains("supportedAxes"))
    }
    @Test fun rejectsExternalEntityDeclarations() {
        assertThrows(IllegalArgumentException::class.java) { FontConfig.parse("""<!DOCTYPE x [<!ENTITY ext SYSTEM "file:///etc/passwd">]><familyset/>""") }
    }
    @Test fun rejectsInvalidScriptTag() {
        assertThrows(IllegalArgumentException::class.java) { FontConfig.patch(xml, true, FontPlan(fallbacks = mapOf("../../invalid" to "a")), listOf(static)) }
    }
    @Test fun presetExpandsRealWeightsAndScopesNotoToSelectedTargets() {
        val regular = static.copy(id = "s", fileName = "sarasa.ttf", family = "sarasa", preset = "sarasa")
        val bold = regular.copy(id = "b", fileName = "bold.ttf", weight = 700, preset = null, hidden = true)
        val arabic = static.copy(id = "n", fileName = "noto.ttf", hidden = true, fallbackGroup = "sarasa", fallbackTags = setOf("und-Arab"))
        val fonts = listOf(regular, bold, arabic)
        val plan = FontPlan(primary = "s")
        assertEquals(setOf("s", "b", "n"), FontSelection.selected(plan, fonts).map { it.id }.toSet())
        val result = FontConfig.patch(xml, true, plan, fonts)
        val named = FontConfig.parse(result).getElementsByTagName("family").item(0) as Element
        assertTrue(named.textContent.contains("GlyphOrder-sarasa.ttf")); assertTrue(named.textContent.contains("GlyphOrder-bold.ttf"))
        val nodes = fontNodes(result)
        val noto = nodes.filter { it.textContent.contains("GlyphOrder-noto.ttf") }
        assertEquals(setOf("400", "700"), nodes.filter { (it.parentNode as Element).getAttribute("name") == "sans-serif" }.map { it.getAttribute("weight") }.toSet())
        assertTrue(noto.all { it.getAttribute("fallbackFor") == "sans-serif" })
        assertTrue(result.indexOf("GlyphOrder-noto.ttf") < result.indexOf("Arabic.ttf"))
        assertFalse(result.contains("supportedAxes"))
        val other = FontConfig.patch(xml, true, FontPlan(primary = "a"), fonts + static)
        assertTrue(other.contains("GlyphOrder-noto.ttf"))
        val without = FontConfig.patch(xml, true, FontPlan(primary = "a", automaticFallback = false), fonts + static)
        assertFalse(without.contains("GlyphOrder-noto.ttf"))
    }

    @Test fun sharesPresetFacesAcrossScriptsWithoutUnqualifiedDuplicates() {
        val regular = static.copy(id = "s", fileName = "sarasa.ttf", family = "sarasa", preset = "sarasa")
        val bold = regular.copy(id = "b", fileName = "bold.ttf", weight = 700, preset = null)
        val noto = static.copy(id = "n", fileName = "noto.ttf", fallbackGroup = "sarasa",
            fallbackTags = setOf("und-Arab", "zh-Hans", "und-Osge"))
        val result = FontConfig.patch(xml, true, FontPlan(primary = "s", targets = setOf("sans-serif", "serif")), listOf(regular, bold, noto))
        val injected = fontNodes(result).filter { it.textContent.contains("GlyphOrder-noto.ttf") }
        assertEquals(2, injected.size) // Two target scopes inside ONE shared XML family.
        assertSame(injected[0].parentNode, injected[1].parentNode)
        val family = injected[0].parentNode as Element
        assertEquals(noto.fallbackTags, family.getAttribute("lang").split(',').toSet())
        assertEquals(setOf("sans-serif", "serif"), injected.map { it.getAttribute("fallbackFor") }.toSet())
        assertTrue(result.indexOf("GlyphOrder-noto.ttf") < result.indexOf("Arabic.ttf"))
        assertTrue(result.indexOf("GlyphOrder-noto.ttf") < result.indexOf("CJK.ttc"))
    }

    @Test fun sharedRoutesKeepVariantAndPerScriptFontOrder() {
        val input = """<familyset><family name="sans-serif"><font>Roboto.ttf</font></family><family lang="und-Arab" variant="elegant"><font>Elegant.ttf</font></family><family lang="und-Arab" variant="compact"><font>Compact.ttf</font></family><family lang="zh-Hans" variant="compact"><font>CJK.ttf</font></family></familyset>"""
        val result = FontConfig.patch(input, true, FontPlan(fallbacks = linkedMapOf("und-Arab" to "a", "zh-Hans" to "a")), listOf(static))
        val injected = fontNodes(result).filter { it.textContent.contains("GlyphOrder-") }.map { it.parentNode as Element }
        assertEquals(2, injected.size)
        assertEquals(setOf("elegant", "compact"), injected.map { it.getAttribute("variant") }.toSet())
        assertEquals(setOf("und-Arab", "zh-Hans"), injected.single { it.getAttribute("variant") == "compact" }.getAttribute("lang").split(',').toSet())
        assertTrue(result.indexOf("GlyphOrder-abc.ttf") < result.indexOf("Elegant.ttf"))
    }

    @Test fun mergingDoesNotMoveAutomaticFontAheadOfManualPreference() {
        val primary = static.copy(id = "s", fileName = "sarasa.ttf", preset = "sarasa")
        val noto = static.copy(id = "n", fileName = "noto.ttf", fallbackGroup = "sarasa", fallbackTags = setOf("und-Arab", "zh-Hans"))
        val result = FontConfig.patch(xml, true, FontPlan(primary = "s", fallbacks = mapOf("zh-Hans" to "v")), listOf(primary, noto, variable))
        val families = fontNodes(result).filter { it.textContent.contains("GlyphOrder-") }
            .map { it.parentNode as Element }.distinct()
            .filter { "zh-Hans" in it.getAttribute("lang").split(',') }
        assertTrue(families.indexOfFirst { it.textContent.contains("GlyphOrder-sarasa.ttf") } < families.indexOfFirst { it.textContent.contains("GlyphOrder-var.otf") })
        assertTrue(families.indexOfFirst { it.textContent.contains("GlyphOrder-var.otf") } < families.indexOfFirst { it.textContent.contains("GlyphOrder-noto.ttf") })
    }

    @Test fun groupedWeightsAndExplicitUniversalOrderAreNotDuplicated() {
        val regular = static.copy(family = "group")
        val bold = regular.copy(id = "b", fileName = "bold.ttf", weight = 700)
        val result = FontConfig.patch(xml, false, FontPlan(universal = listOf("a", "b", "v")), listOf(regular, bold, variable))
        val families = fontNodes(result).filter { it.textContent.contains("GlyphOrder-") }.map { it.parentNode }.distinct()
        assertEquals(2, families.size)
        assertTrue(families.first().textContent.contains("GlyphOrder-bold.ttf"))
        assertTrue(families.last().textContent.contains("GlyphOrder-var.otf"))
    }

    @Test fun globalModeUpdatesNamedListsProductAndScriptPriority() {
        val input = xml.replace("</familyset>", "<family-list name=\"custom\"><family><font>A.ttf</font></family><family><font>B.ttf</font></family></family-list></familyset>")
        val plan = FontSelection.forDevice(FontPlan(primary = "a", global = true), listOf("sans-serif", "serif", "custom", "product"))
        assertEquals(4, plan.targets.size)
        assertTrue("custom" in FontConfig.families(input))
        val result = FontConfig.patch(input, true, plan, listOf(static))
        val list = FontConfig.parse(result).getElementsByTagName("family-list").item(0) as Element
        assertEquals(1, list.getElementsByTagName("family").length)
        assertTrue(list.textContent.contains("GlyphOrder-abc.ttf"))
        assertTrue(result.indexOf("GlyphOrder-abc.ttf") < result.indexOf("Arabic.ttf"))
        assertFalse(result.contains("Serif.ttf"))
        assertTrue(result.contains("NotoColorEmoji.ttf")); assertTrue(result.contains("name=\"sans\""))
        val product = "<fonts-modification><family name=\"product\" customizationType=\"new-named-family\"><font>Old.ttf</font></family></fonts-modification>"
        assertTrue(FontConfig.patch(product, false, plan, listOf(static), true).contains("GlyphOrder-abc.ttf"))
    }

    private val compactXml = "<familyset><family name=\"sans-serif\"><font>Roboto.ttf</font></family><family lang=\"und-Arab\"><font>Old.ttf</font></family><family lang=\"und-Zsye\"><font>NotoColorEmoji.ttf</font></family></familyset>"
    private val compactPrimary = static.copy(id = "s", preset = "sarasa", coverage = listOf(65..66))
    private val compactNoto = static.copy(id = "n", fileName = "noto.ttf", fallbackGroup = "sarasa", fallbackTags = setOf("und-Arab"), coverage = listOf(67..70))

    @Test fun automaticallyReusesSystemCoverageWithoutAddingRedundantFaces() {
        val result = FontConfig.patch(compactXml, true, FontPlan(primary = "s"), listOf(compactPrimary, compactNoto),
            systemCoverage = mapOf("/system/fonts/Old.ttf#0" to listOf(67..70)))
        assertFalse(result.contains("GlyphOrder-noto.ttf"))
        assertTrue(result.contains("Old.ttf"))
    }

    @Test fun automaticallyReusesOriginalSlotWhenAllOldGlyphsArePreserved() {
        val result = FontConfig.patch(compactXml, true, FontPlan(primary = "s"), listOf(compactPrimary, compactNoto),
            systemCoverage = mapOf("/system/fonts/Old.ttf#0" to listOf(67..69)))
        val noto = fontNodes(result).single { it.textContent.contains("GlyphOrder-noto.ttf") }
        val parent = noto.parentNode as Element
        assertTrue(parent.textContent.contains("Old.ttf")) // Other targets retain the original.
        assertEquals("sans-serif", noto.getAttribute("fallbackFor"))
        assertEquals(4, FontConfig.parse(result).getElementsByTagName("family").length)
    }

    @Test fun refusesSlotReuseWithCoverageGapsOrMissingMetadata() {
        listOf(emptyMap(), mapOf("/system/fonts/Old.ttf#0" to listOf(67..69, 72..72))).forEach { coverage ->
            val result = FontConfig.patch(compactXml, true, FontPlan(primary = "s"), listOf(compactPrimary, compactNoto), systemCoverage = coverage)
            val noto = fontNodes(result).single { it.textContent.contains("GlyphOrder-noto.ttf") }
            assertFalse(noto.parentNode.textContent.contains("Old.ttf"))
            assertEquals(5, FontConfig.parse(result).getElementsByTagName("family").length)
        }
    }

    @Test fun slotReuseUsesCurrentCoverageAndCannotMakeCircularAssumptions() {
        val input = compactXml.replace("<family lang=\"und-Zsye\">", "<family lang=\"und-Osge\"><font>Other.ttf</font></family><family lang=\"und-Zsye\">")
        val other = compactNoto.copy(id = "o", fileName = "other-noto.ttf", fallbackTags = setOf("und-Osge"), coverage = listOf(80..81))
        val result = FontConfig.patch(input, true, FontPlan(primary = "s"), listOf(compactPrimary, compactNoto, other),
            systemCoverage = mapOf("/system/fonts/Old.ttf#0" to listOf(67..69, 72..72), "/system/fonts/Other.ttf#0" to listOf(72..72, 80..80)))
        val first = fontNodes(result).single { it.textContent.contains("GlyphOrder-noto.ttf") }
        val second = fontNodes(result).single { it.textContent.contains("GlyphOrder-other-noto.ttf") }
        assertTrue(first.parentNode.textContent.contains("Old.ttf")) // Other.ttf still guarantees 72.
        assertFalse(second.parentNode.textContent.contains("Other.ttf")) // Replacing it would now lose 72.
    }
}

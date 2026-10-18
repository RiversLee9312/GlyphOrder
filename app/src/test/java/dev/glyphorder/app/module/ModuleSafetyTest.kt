package dev.glyphorder.app.module

import dev.glyphorder.app.font.FontInfo
import dev.glyphorder.app.font.FontPlan
import dev.glyphorder.app.font.CmapCoverage
import dev.glyphorder.app.font.FontSelection
import dev.glyphorder.app.font.FontAxis
import org.w3c.dom.Element
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModuleSafetyTest {
    private val legacy = "/system/etc/fonts.xml"
    private val modern = "/system/etc/font_fallback.xml"
    private val product = "/product/etc/fonts_customization.xml"
    private fun system(fallbacks: Int, extraNamed: String = "", font: String = "<font>Fallback.ttf</font>") =
        "<familyset><family name=\"sans-serif\"><font>Roboto.ttf</font></family>$extraNamed" +
            (0 until fallbacks).joinToString("") { "<family lang=\"und-Arab\" variant=\"compact\">$font</family>" } + "</familyset>"

    private fun rejected(configs: Map<String, String>): IllegalArgumentException =
        assertThrows(IllegalArgumentException::class.java) { ModuleSafety.validate(configs) }.also {
            assertTrue(it.message.orEmpty().contains("error_font_family_limit"))
        }

    @Test fun accepts254AndRejects255IncludingNamedPrimary() {
        ModuleSafety.validate(mapOf(legacy to system(253)))
        val failure = rejected(mapOf(legacy to system(254)))
        assertTrue(failure.message.orEmpty().endsWith("|sans-serif|255|254"))
    }

    @Test fun checksEveryNamedChainIncludingTheFirstFamilyFallback() {
        val other = "<family name=\"serif\"><font>Serif.ttf</font></family>"
        ModuleSafety.validate(mapOf(legacy to system(252, other)))
        val failure = rejected(mapOf(legacy to system(253, other)))
        assertTrue(failure.message.orEmpty().contains("|serif|255|254"))
    }

    @Test fun languagesAndVariantsDoNotHideFamiliesFromTheLimit() {
        val xml = system(254).replaceFirst("lang=\"und-Arab\"", "lang=\"zh-Hans,ja,ko\"")
        rejected(mapOf(legacy to xml))
    }

    @Test fun weightsCountAsOneFamilyAndAliasesDoNotAddFamilies() {
        val fonts = (100..900 step 100).joinToString("") { "<font weight=\"$it\">F$it.ttf</font>" }
        val xml = system(253, font = fonts).replace("</familyset>", "<alias name=\"sans\" to=\"sans-serif\"/></familyset>")
        ModuleSafety.validate(mapOf(legacy to xml))
    }

    @Test fun targetedFallbackIsOnlyCountedOnceForItsTarget() {
        val scoped = "<family><font fallbackFor=\"serif\">SerifOnly.ttf</font></family>"
        val xml = system(253).replace("</familyset>", "$scoped</familyset>")
        ModuleSafety.validate(mapOf(legacy to xml))
        val withSerif = xml.replace("</familyset>", "<family name=\"serif\"><font>Serif.ttf</font></family></familyset>")
        assertTrue(rejected(mapOf(legacy to withSerif)).message.orEmpty().contains("|serif|256|254"))
    }

    @Test fun defaultAndSpecificFontsInOneFamilyUseOneSlotPerChain() {
        ModuleSafety.validate(mapOf(legacy to system(253, font = "<font>Default.ttf</font><font fallbackFor=\"sans-serif\">Specific.ttf</font>")))
    }

    @Test fun productNamesAndOverridesParticipateInValidation() {
        val customization = """<fonts-modification><family customizationType="new-named-family" name="product"><font>Product.ttf</font></family></fonts-modification>"""
        assertTrue(rejected(mapOf(legacy to system(253), product to customization)).message.orEmpty().contains("|product|255|254"))
        // Overriding sans-serif replaces its own family but the original first family
        // remains in the global list, so this chain now has one additional slot.
        rejected(mapOf(legacy to system(253), product to customization.replace("name=\"product\"", "name=\"sans-serif\"")))
    }

    @Test fun checksModernAndLegacyIndependently() {
        assertTrue(rejected(mapOf(legacy to system(1), modern to system(254))).message.orEmpty().contains(modern))
        assertTrue(rejected(mapOf(legacy to system(254), modern to system(1))).message.orEmpty().contains(legacy))
    }

    @Test fun countsMultiFamilyNamedListsAndCustomFallbacks() {
        val list = "<family-list name=\"serif\"><family><font>A.ttf</font></family><family><font>B.ttf</font></family></family-list>"
        rejected(mapOf(legacy to system(252, list)))
        val custom = "<family-list name=\"custom\" fallback=\"sans-serif\"><family><font>C.ttf</font></family></family-list>"
        rejected(mapOf(legacy to system(253, custom)))
    }

    @Test fun localeCustomizationsAreIncludedConservatively() {
        val customization = """<fonts-modification><family customizationType="new-locale-family" lang="und-Arab" operation="append"><font>Arabic.ttf</font></family></fonts-modification>"""
        rejected(mapOf(legacy to system(253), product to customization))
    }

    @Test fun ignoredOrEmptyFamilyDoesNotHideTheFirstNamedListFallback() {
        val initial = "<family-list name=\"initial\"><family><font>A.ttf</font></family><family><font>B.ttf</font></family></family-list>"
        val serif = "<family name=\"serif\"><font>Serif.ttf</font></family>"
        listOf("<family ignore=\"true\"><font>Ignored.ttf</font></family>", "<family/>").forEach { skipped ->
            val xml = system(252).replace("<family name=\"sans-serif\"><font>Roboto.ttf</font></family>", skipped + initial + serif)
            rejected(mapOf(legacy to xml))
        }
    }

    private fun archive(configs: Map<String, String>, block: (File) -> Unit) {
        val file = File.createTempFile("module-safety-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                configs.forEach { (path, xml) ->
                    val overlay = if (path.startsWith("/product/")) "system$path" else path.removePrefix("/")
                    zip.putNextEntry(ZipEntry(overlay)); zip.write(xml.toByteArray()); zip.closeEntry()
                }
            }
            block(file)
        } finally { file.delete() }
    }

    @Test fun installPreflightRejectsUnsafeOldZipEvenWithMatchingConfigSet() {
        archive(mapOf(legacy to system(254), modern to system(1))) { file ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                ModuleSafety.validateArchive(file, setOf(legacy, modern))
            }
            assertTrue(failure.message.orEmpty().contains("error_font_family_limit"))
        }
    }

    @Test fun installPreflightAcceptsSafeZipAndRejectsMissingOrUnexpectedConfigs() {
        archive(mapOf(legacy to system(1))) { file ->
            ModuleSafety.validateArchive(file, setOf(legacy))
            assertThrows(IllegalArgumentException::class.java) { ModuleSafety.validateArchive(file, setOf(legacy, modern)) }
        }
        archive(mapOf(legacy to system(1), modern to system(1))) { file ->
            assertThrows(IllegalArgumentException::class.java) { ModuleSafety.validateArchive(file, setOf(legacy)) }
        }
    }

    private fun fixture(name: String) = javaClass.getResourceAsStream("/bootloop/$name")!!.bufferedReader().use { it.readText() }
    private fun ranges(text: String) = text.split(',').filter(String::isNotBlank).map {
        val ends = it.split('-'); ends.first().toInt()..ends.last().toInt()
    }
    private fun fixtureFonts(name: String): List<FontInfo> {
        val names = fixture("font-names.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { line -> val f = line.split('\t'); f[0] to f[1] }
        return fixture(name).lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f = line.split('\t')
            val axes = f.getOrNull(8).orEmpty().split(',').filter(String::isNotBlank).map {
                val a = it.split(':'); FontAxis(a[0], a[1].toFloat(), a[2].toFloat(), a[3].toFloat())
            }
            FontInfo(f[0], f[1], names[f[0]] ?: f[1], f[1], f[6].toInt(), false, axes, 100, emptySet(),
                family = f[2].ifBlank { null }, preset = f[3].ifBlank { null }, fallbackGroup = f[4].ifBlank { null },
                fallbackTags = f[5].split(',').filter(String::isNotBlank).toSet(), coverage = ranges(f[7]))
        }.toList()
    }

    private fun assertFallbackCoverage(configs: Map<String, String>, targets: Set<String>, fonts: List<FontInfo>, systemCoverage: Map<String, List<IntRange>>) {
        val customCoverage = fonts.associate { "GlyphOrder-${it.fileName}" to it.coverage }
        val expected = CmapCoverage.union(fonts.map { it.coverage })
        listOf(legacy, modern).forEach { path ->
            val families = FontConfig.parse(configs.getValue(path)).getElementsByTagName("family").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
            }
            targets.forEach { target ->
                val actual = CmapCoverage.union(families.filter { !it.hasAttribute("name") || it.getAttribute("name") == target }.map { family ->
                    val nodes = family.getElementsByTagName("font").let { n -> (0 until n.length).map { n.item(it) as Element } }
                    val specific = nodes.filter { it.getAttribute("fallbackFor") == target }
                    val selected = specific.ifEmpty { nodes.filter { !it.hasAttribute("fallbackFor") } }
                    CmapCoverage.intersection(selected.map { font ->
                        val name = font.textContent.trim()
                        customCoverage[name] ?: systemCoverage["/system/fonts/$name#${font.getAttribute("index").ifEmpty { "0" }}"].orEmpty()
                    })
                })
                assertTrue("Automatic coverage must survive optimization: $path / $target", CmapCoverage.contains(actual, expected))
            }
        }
    }

    @Test fun everyBuiltinAndArbitraryImportFitsDefaultFallbacksWithoutLosingCharacters() {
        val baseline = mapOf(legacy to fixture("fonts.xml"), modern to fixture("font_fallback.xml"), product to fixture("fonts_customization.xml"))
        val systemCoverage = fixture("system-coverage.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { line -> val f = line.split('\t'); f[0] to ranges(f[1]) }
        val sarasa = fixtureFonts("sarasa-preset.tsv")
        val builtins = fixtureFonts("builtin-primaries.tsv") + sarasa.filter { it.fallbackGroup == null }
        val imported = builtins.first().copy(id = "imported", fileName = "own.ttf", name = "Imported Latin font",
            preset = null, family = null, axes = emptyList(), coverage = listOf(32..126))
        val fonts = (builtins + sarasa.filter { it.fallbackGroup != null } + imported).sortedBy { it.name }
        val targets = baseline.values.flatMap(FontConfig::families).toSet()
        (builtins.filter { it.weight == 400 } + imported).forEach { primary ->
            val plan = FontPlan(primary = primary.id, global = true, targets = targets)
            assertEquals(167, FontSelection.dependencies(plan, fonts).size)
            val configs = baseline.mapValues { (path, xml) -> FontConfig.patch(xml, path == modern, plan, fonts, path == product, systemCoverage) }
            val maximum = ModuleSafety.validate(configs)
            assertTrue(maximum <= 254)
            val selected = FontSelection.selected(plan, fonts)
            assertFallbackCoverage(configs, targets, selected, systemCoverage)
            archive(configs) { file -> ModuleSafety.validateArchive(file, expectedConfigs = baseline.keys) }
            println("${primary.name}: default automatic fallback maximum $maximum/254; all selected cmap coverage retained.")
        }
    }

    @Test fun replaysOnePlusBootloopPresetAndBlocksItWithoutDroppingFonts() {
        val baseline = mapOf(legacy to fixture("fonts.xml"), modern to fixture("font_fallback.xml"), product to fixture("fonts_customization.xml"))
        ModuleSafety.validate(baseline)
        val fonts = fixture("sarasa-preset.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f = line.split('\t')
            FontInfo(f[0], f[1], f[1], f[1], f[6].toInt(), false, emptyList(), 100, emptySet(),
                family = f[2].ifBlank { null }, preset = f[3].ifBlank { null }, fallbackGroup = f[4].ifBlank { null },
                fallbackTags = f[5].split(',').filter(String::isNotBlank).toSet(), coverage = ranges(f[7]))
        }.toList()
        assertEquals(167, fonts.count { it.fallbackGroup == "sarasa" })
        val plan = FontPlan(primary = fonts.single { it.preset == "sarasa" }.id,
            targets = baseline.values.flatMap(FontConfig::families).toSet())
        val updated = baseline.mapValues { (path, xml) -> FontConfig.patch(xml, path == modern, plan, fonts, path == product) }
        val modernXml = updated.getValue(modern)
        val count = FontConfig.parse(modernXml).getElementsByTagName("family").length
        assertTrue("Previously 772 XML families; duplicate script/universal expansion must shrink: $count", count < 500)
        fonts.filter { it.fallbackGroup == "sarasa" }.forEach { assertTrue(modernXml.contains("GlyphOrder-${it.fileName}")) }
        rejected(updated) // All 167 supplements plus system defaults still cannot safely fit.
        archive(updated) { file ->
            assertThrows(IllegalArgumentException::class.java) { ModuleSafety.validateArchive(file, baseline.keys) }
        }
        val smallerFonts = fonts.filter { it.fallbackGroup == null } + fonts.filter { it.fallbackGroup != null }.take(2)
        val smaller = baseline.mapValues { (path, xml) -> FontConfig.patch(xml, path == modern, plan, smallerFonts, path == product) }
        ModuleSafety.validate(smaller)
        println("Crash preset: 772 -> $count XML families; oversized configuration rejected, smaller configuration accepted.")

        val systemCoverage = fixture("system-coverage.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { line -> val f = line.split('\t'); f[0] to ranges(f[1]) }
        val optimizedPlan = FontSelection.forDevice(plan.copy(global = true), plan.targets.toList())
        val optimized = baseline.mapValues { (path, xml) -> FontConfig.patch(xml, path == modern, optimizedPlan, fonts, path == product, systemCoverage) }
        val maximum = ModuleSafety.validate(optimized)
        assertTrue(maximum <= 254)
        archive(optimized) { file -> ModuleSafety.validateArchive(file, baseline.keys) }
        val customCoverage = fonts.associate { "GlyphOrder-${it.fileName}" to it.coverage }
        val expectedCoverage = CmapCoverage.union(fonts.map { it.coverage })
        listOf(legacy, modern).forEach { path ->
            val families = FontConfig.parse(optimized.getValue(path)).getElementsByTagName("family").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
            }
            optimizedPlan.targets.forEach { target ->
                val actual = CmapCoverage.union(families.filter { !it.hasAttribute("name") || it.getAttribute("name") == target }.map { family ->
                    val nodes = family.getElementsByTagName("font").let { n -> (0 until n.length).map { n.item(it) as Element } }
                    val specific = nodes.filter { it.getAttribute("fallbackFor") == target }
                    val selected = specific.ifEmpty { nodes.filter { !it.hasAttribute("fallbackFor") } }
                    CmapCoverage.intersection(selected.map { font ->
                        val name = font.textContent.trim()
                        customCoverage[name] ?: systemCoverage["/system/fonts/$name#${font.getAttribute("index").ifEmpty { "0" }}"].orEmpty()
                    })
                })
                assertTrue("Preset coverage must survive optimization: $path / $target", CmapCoverage.contains(actual, expectedCoverage))
            }
        }
        println("Optimized global crash preset maximum native chain: $maximum / 254; full bundled cmap coverage retained.")
    }
}

package dev.glyphorder.app

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.text.TextRunShaper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import dev.glyphorder.app.ui.previewSpans
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import java.util.zip.ZipFile

/** Read-only system QA: generated archives and native shaping, no module installation/reboot. */
@RunWith(AndroidJUnit4::class)
class StyleCompositionTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()
    private val context get() = fixtures.context

    @Test fun casualAndCursiveCanBeChangedIndependently() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        val rubik = fonts.single { it.name == "Rubik" && !it.hidden }
        val serif = fonts.single { it.name == "Noto Serif" && !it.hidden }
        val snapshot = DeviceRepository().inspect()
        assertTrue(snapshot.targets.containsAll(listOf("casual", "cursive")))
        val requested = FontPlan(styleMode = true, styleFonts = mapOf("casual" to rubik.id, "cursive" to serif.id))
        val module = ModuleBuilder(context).build(snapshot, requested, fonts, store.directory)
        assertTrue(module.maxFamilies <= 254)
        ZipFile(module.file).use { zip ->
            snapshot.configs.filterKeys { !it.startsWith("/product/") }.forEach { (path, baseline) ->
                val xml = zip.getInputStream(zip.getEntry(DeviceRepository.overlayPath(path))).bufferedReader().use { it.readText() }
                fun families(text: String) = FontConfig.parse(text).getElementsByTagName("family").let { list ->
                    (0 until list.length).map { list.item(it) as Element }
                }
                val patched = families(xml)
                val stock = families(baseline)
                listOf("sans-serif", "serif", "monospace").forEach { name ->
                    assertEquals(FontConfig.serialize(stock.single { it.getAttribute("name") == name }),
                        FontConfig.serialize(patched.single { it.getAttribute("name") == name }))
                }
                mapOf("casual" to rubik, "cursive" to serif).forEach { (name, font) ->
                    val nodes = patched.single { it.getAttribute("name") == name }.getElementsByTagName("font")
                    assertTrue(nodes.length > 0)
                    for (i in 0 until nodes.length) assertEquals("GlyphOrder-${font.fileName}", nodes.item(i).textContent.trim())
                }
            }
        }
        val onlyCursive = FontSelection.forDevice(requested.copy(styleFonts = requested.styleFonts - "casual"), snapshot.targets)
        assertEquals(setOf("cursive"), onlyCursive.targets)
    }

    @Test fun rubikGeneratedDefaultIsNativeRegularInBothXmlDialects() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        val rubik = fonts.single { it.name == "Rubik" && !it.hidden }
        assertEquals(300f, rubik.axes.single { it.tag == "wght" }.default)
        val xml = "<familyset><family name=\"sans-serif\"><font>Roboto.ttf</font></family></familyset>"
        listOf(true, false).forEach { modern ->
            val patched = FontConfig.patch(xml, modern, FontPlan(primary = rubik.id, automaticFallback = false), fonts)
            val first = FontConfig.parse(patched).getElementsByTagName("font").item(0) as Element
            val axes = first.getElementsByTagName("axis")
            val variation = (0 until axes.length).map { axes.item(it) as Element }
                .joinToString(",") { "'${it.getAttribute("tag")}' ${it.getAttribute("stylevalue")}" }
            val font = Font.Builder(rubik.file(store.directory)).setWeight(first.getAttribute("weight").toInt())
                .setFontVariationSettings(variation).build()
            val typeface = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
            val glyphs = TextRunShaper.shapeTextRun("Rubik 012345", 0, 12, 0, 12, 0f, 0f, false,
                Paint().apply { this.typeface = typeface; textSize = 48f })
            assertTrue(glyphs.glyphCount() > 0)
            for (i in 0 until glyphs.glyphCount()) assertEquals(400, glyphs.getFont(i).style.weight)
            fun outline(face: Typeface): FloatArray = Path().also { path ->
                Paint().apply { this.typeface = face; textSize = 48f }.getTextPath("Rubik", 0, 5, 0f, 0f, path)
            }.approximate(0.1f)
            fun expected(weight: Int): Typeface = Typeface.CustomFallbackBuilder(FontFamily.Builder(
                Font.Builder(rubik.file(store.directory)).setWeight(weight).setFontVariationSettings("'wght' $weight").build()).build())
                .setStyle(android.graphics.fonts.FontStyle(weight, 0)).build()
            // Native PositionedGlyphs may canonicalize away reported axes. Compare
            // actual glyph outlines against independent regular and light instances.
            assertArrayEquals(outline(expected(400)), outline(typeface), 0.001f)
            assertFalse(outline(expected(300)).contentEquals(outline(typeface)))
        }
    }

    @Test fun completeFamiliesUseTheirOwnDesignsAndMixedModulesFitNativeLimits() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        val builtins = fonts.filter { store.isBundled(it) && !it.hidden }
        assertEquals(setOf("MiSans", "Rubik", "Noto Sans", "Noto Serif", "Sarasa Fixed"), builtins.map { it.name }.toSet())
        val serif = builtins.single { it.name == "Noto Serif" }
        val misans = builtins.single { it.name == "MiSans" }
        fun shapedFiles(primary: FontInfo, text: String, language: String): Set<String> {
            val span = previewSpans(text, fonts, FontPlan(primary = primary.id), store.directory, 400, language)
            val paint = android.text.TextPaint().apply { textSize = 48f }
            val paths = mutableSetOf<String>()
            android.text.TextShaper.shapeText(span, 0, span.length, android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR, paint) { _, _, glyphs, _ ->
                for (i in 0 until glyphs.glyphCount()) {
                    assertTrue(glyphs.getGlyphId(i) != 0)
                    paths += glyphs.getFont(i).file!!.name
                }
            }
            return paths
        }
        val serifFiles = FontSelection.dependencies(FontPlan(primary = serif.id), fonts)
            .filter { it.fallbackGroup == "noto-serif" }.flatMap { FontSelection.family(it, fonts) }.map { it.fileName }.toSet()
        listOf("中文" to "zh-Hans", "देवनागरी" to "hi", "ภาษาไทย" to "th", "עברית" to "he").forEach { (text, lang) ->
            val paths = shapedFiles(serif, text, lang)
            assertTrue("Serif designs for $text: $paths", paths.all { it in serifFiles })
        }
        val miFiles = FontSelection.dependencies(FontPlan(primary = misans.id), fonts)
            .filter { it.fallbackGroup == "misans-global" }.flatMap { FontSelection.family(it, fonts) }.map { it.fileName }.toSet()
        assertTrue(miFiles.isNotEmpty())
        assertTrue(shapedFiles(misans, "سلام", "ar").all { it in miFiles })

        val snapshot = DeviceRepository().inspect()
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val plans = builtins.map { FontPlan(primary = it.id, global = true) } +
            FontPlan(styleMode = true, styleFonts = mapOf("sans" to misans.id, "serif" to serif.id,
                "mono" to builtins.single { it.name == "Sarasa Fixed" }.id,
                "google" to builtins.single { it.name == "Rubik" }.id))
        plans.forEach { requested ->
            println("Generating composition: ${requested.primary?.let { id -> fonts.find { it.id == id }?.name } ?: "mixed"}")
            val module = try { ModuleBuilder(context).build(snapshot, requested, fonts, store.directory) } catch (e: Exception) {
                val diagnostic = java.io.File(context.cacheDir, "style-debug").apply { mkdirs() }
                val effective = FontSelection.forDevice(requested, snapshot.targets)
                snapshot.configs.forEach { (path, xml) ->
                    java.io.File(diagnostic, path.substringAfterLast('/')).writeText(FontConfig.patch(xml, path.endsWith("font_fallback.xml"),
                        effective, fonts, path.startsWith("/product/"), snapshot.fontCoverage, snapshot.targets.toSet()))
                }
                android.util.Log.e("GlyphOrderTest", "Failed ${requested.primary?.let { id -> fonts.find { it.id == id }?.name } ?: "mixed"}")
                throw e
            }
            assertTrue(module.maxFamilies <= 254)
            val plan = FontSelection.forDevice(requested, snapshot.targets)
            val expected = FontStyles.assignments(plan, snapshot.targets)
            ZipFile(module.file).use { zip ->
                snapshot.configs.forEach { (path, _) ->
                    val updated = zip.getInputStream(zip.getEntry(DeviceRepository.overlayPath(path))).bufferedReader().use { it.readText() }
                    val nodes = FontConfig.parse(updated).getElementsByTagName("font")
                    for (i in 0 until nodes.length) {
                        val node = nodes.item(i) as Element
                        val name = node.textContent.trim()
                        if (name.startsWith("GlyphOrder-")) {
                            val dir = if (path.startsWith("/product/")) "system/product/fonts/" else "system/fonts/"
                            assertNotNull("Referenced face must be packaged: $name", zip.getEntry(dir + name))
                        }
                        val family = node.parentNode as Element
                        val target = family.getAttribute("name")
                        if (target in expected) {
                            val primary = fonts.single { it.id == expected[target] }
                            assertTrue("Named target $target: $name", FontSelection.family(primary, fonts).any { name == "GlyphOrder-${it.fileName}" })
                        }
                    }
                    if (!path.startsWith("/product/")) {
                        val custom = fonts.associate { "GlyphOrder-${it.fileName}" to it.coverage }
                        val families = FontConfig.parse(updated).getElementsByTagName("family").let { list ->
                            (0 until list.length).map { list.item(it) as Element }
                        }
                        expected.entries.groupBy({ it.value }, { it.key }).forEach { (id, targets) ->
                            val primary = fonts.single { it.id == id }
                            val required = CmapCoverage.union(FontSelection.selected(plan.copy(primary = id, styleMode = false), fonts).map { it.coverage })
                            val base = CmapCoverage.intersection(FontSelection.family(primary, fonts).map { it.coverage })
                            targets.forEach { target ->
                                val actual = CmapCoverage.union(listOf(base) + families.filter { !it.hasAttribute("name") }.map { family ->
                                    val faces = family.getElementsByTagName("font").let { list -> (0 until list.length).map { list.item(it) as Element } }
                                    val selected = faces.filter { it.getAttribute("fallbackFor") == target }
                                        .ifEmpty { faces.filter { !it.hasAttribute("fallbackFor") } }
                                    CmapCoverage.intersection(selected.map { face ->
                                        val name = face.textContent.trim()
                                        custom[name] ?: snapshot.fontCoverage["${if (name.startsWith('/')) name else "/system/fonts/$name"}#${face.getAttribute("index").ifEmpty { "0" }}"].orEmpty()
                                    })
                                })
                                assertTrue("Full selected character coverage: $path / $target / ${primary.name}", CmapCoverage.contains(actual, required))
                            }
                        }
                    }
                }
            }
            println("Composition ${requested.primary ?: "mixed"}: ${module.maxFamilies}/254 families; ${module.file.length()} bytes")
        }
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
    }

    @Test fun mixedPreferencesRoundTripAndLegacyPlanRetainsScope() {
        val prefs = context.getSharedPreferences("fonts", 0)
        val previous = prefs.getString("plan", null)
        try {
            val store = FontStore(context)
            prefs.edit().putString("plan", """{"primary":"rubik","global":true,"targets":["sans-serif"]}""").commit()
            val legacy = store.load()
            assertFalse(legacy.styleMode); assertTrue(legacy.global)
            val mixed = legacy.copy(styleMode = true, styleFonts = mapOf("sans" to "s", "serif" to "r", "mono" to "m", "google" to "g"))
            store.save(mixed)
            assertEquals(mixed, store.load())
            assertEquals(mixed.styleFonts, FontSelection.defaultFallbacks(mixed).styleFonts)
            prefs.edit().putString("plan", """{"styleMode":true,"styleFonts":{"other":"legacy-font"}}""").commit()
            val migrated = store.load()
            assertEquals("legacy-font", migrated.styleFonts["casual"])
            assertEquals("legacy-font", migrated.styleFonts["cursive"])
            store.save(migrated.copy(styleFonts = migrated.styleFonts - "casual"))
            assertNull(store.load().styleFonts["casual"])
            assertEquals("legacy-font", store.load().styleFonts["cursive"])
        } finally {
            prefs.edit().apply { if (previous == null) remove("plan") else putString("plan", previous) }.commit()
        }
    }
}

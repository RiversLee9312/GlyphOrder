package dev.glyphorder.app

import android.graphics.Paint
import android.graphics.text.TextRunShaper
import android.text.style.TypefaceSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import dev.glyphorder.app.ui.previewSpans
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class CombinationIntegrationTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()
    private val context get() = fixtures.context

    @Test fun combinationsAndAssignmentsRoundTripWithoutChangingTheLegacyChoices() {
        val store = FontStore(context)
        val font = FontInfo("rubik", "r.ttf", "Rubik", "Rubik", 400, false, emptyList(), 100, emptySet())
        val fallback = font.copy(id = "misans", name = "MiSans")
        val legacy = FontPlan(primary = font.id, universal = listOf(fallback.id), styleMode = true,
            styleFonts = mapOf("sans" to font.id))
        store.save(legacy)
        val (migrated, catalog) = store.combinations(listOf(font, fallback))
        assertEquals(listOf("rubik", "misans"), catalog.single { it.id == migrated.combinationId }.fonts)
        assertEquals(listOf("rubik"), catalog.single { it.id == migrated.styleCombinations["sans"] }.fonts)
        val changed = catalog.map { if (it.id == migrated.combinationId) it.copy(name = "My combination", fonts = it.fonts.reversed()) else it }
        store.saveCombinations(changed)
        val (loaded, restored) = store.combinations(listOf(font, fallback))
        assertEquals(migrated, loaded)
        assertEquals(changed, restored)
    }

    @Test fun rubikThenMiSansUsesMiSansForEveryChineseGlyphAndRubikForLatin() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        val rubik = fonts.single { it.name == "Rubik" && !it.hidden }
        val misans = fonts.single { it.name == "MiSans" && !it.hidden }
        val combination = FontCombination("reference", "Reference", listOf(rubik.id, misans.id))
        for ((sample, expected) in listOf("中文字体简体繁體仿宋契丹小字" to misans, "Rubik012345" to rubik)) {
            val text = previewSpans(sample, fonts, combination.preview(FontPlan(), store.library(fonts), fonts), store.directory, 400, "zh-Hans")
            val typeface = text.getSpans(0, sample.length, TypefaceSpan::class.java).first().typeface!!
            val glyphs = TextRunShaper.shapeTextRun(sample, 0, sample.length, 0, sample.length, 0f, 0f, false,
                Paint().apply { this.typeface = typeface; textSize = 32f })
            for (i in 0 until glyphs.glyphCount()) {
                assertNotEquals(0, glyphs.getGlyphId(i))
                assertEquals("$sample glyph $i", expected.fileName, glyphs.getFont(i).file!!.name)
            }
        }
    }

    @Test fun generatedCombinationModuleContainsOnlyTheChosenFontsAndPreservesLiveConfiguration() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        val rubik = fonts.single { it.name == "Rubik" && !it.hidden }
        val misans = fonts.single { it.name == "MiSans" && !it.hidden }
        val serif = fonts.single { it.name == "Noto Serif" && !it.hidden }
        val catalog = listOf(FontCombination("sans", "Sans", listOf(rubik.id, misans.id)), FontCombination("serif", "Serif", listOf(serif.id)))
        val plan = FontPlan(styleMode = true, styleCombinations = mapOf("sans" to "sans", "serif" to "serif"))
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val snapshot = DeviceRepository().inspect()
        val resolved = CombinationPlan.resolve(plan, catalog, store.library(fonts), fonts)
        val module = ModuleBuilder(context).build(snapshot, resolved, fonts, store.directory)
        ModuleSafety.validateArchive(module.file, snapshot.configs.keys)
        assertTrue(module.maxFamilies in 1..254)
        val expected = FontSelection.selected(resolved, fonts).map { "system/fonts/GlyphOrder-${it.fileName}" }.toSet()
        ZipFile(module.file).use { zip ->
            val packaged = zip.entries().asSequence().map { it.name }.filter { it.startsWith("system/fonts/GlyphOrder-") }.toSet()
            assertEquals(expected, packaged)
        }
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
    }
}

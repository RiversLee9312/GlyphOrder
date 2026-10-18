package dev.glyphorder.app

import android.graphics.fonts.Font
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile
import android.text.TextShaper
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import dev.glyphorder.app.ui.previewSpans

@RunWith(AndroidJUnit4::class)
class BundledFontsTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()
    @Test fun bundledFacesLoadOfflineWithVariableAxesAndLicenses() {
        val context = fixtures.context
        val store = FontStore(context)
        store.ensureBundledFonts()
        val builtins = store.all().filter { store.isBundled(it) && it.name != "Sarasa Fixed" && !it.hidden }
        assertEquals(setOf("MiSans", "Rubik", "Noto Sans", "Noto Serif"), builtins.map { it.name }.toSet())
        builtins.forEach { info ->
            assertTrue(info.variable)
            assertTrue(info.axes.any { it.tag == "wght" })
            assertNotNull(Font.Builder(info.file(store.directory)).build())
            assertTrue(store.license(info)!!.second.length > 1000)
            assertThrows(IllegalStateException::class.java) { store.delete(info) }
        }
        val snapshot = DeviceRepository().inspect()
        val module = ModuleBuilder(context).build(snapshot,
            FontPlan(primary = builtins.first().id, universal = builtins.drop(1).map { it.id }), builtins, store.directory)
        ZipFile(module.file).use { zip ->
            builtins.forEach { assertNotNull(zip.getEntry("system/fonts/GlyphOrder-${it.fileName}")) }
            listOf("MiSans-license.txt", "MiSans-license-original.pdf", "Rubik-OFL.txt", "NotoSerif-OFL.txt")
                .forEach { assertNotNull(zip.getEntry("licenses/$it")) }
        }
    }

    @Test fun sarasaUsesItsOwnGlyphsThenOfflineNotoAndKeepsEmoji() {
        val context = fixtures.context
        val store = FontStore(context)
        store.ensureBundledFonts()
        val fonts = store.all()
        assertEquals(setOf("MiSans", "Rubik", "Noto Sans", "Noto Serif", "Sarasa Fixed"), fonts.filter { store.isBundled(it) && !it.hidden }.map { it.name }.toSet())
        val sarasa = fonts.single { it.preset == "sarasa" }
        val plan = FontPlan(primary = sarasa.id)
        val family = FontSelection.family(sarasa, fonts)
        assertEquals(setOf(400, 700), family.map { it.weight }.toSet())
        val supplements = FontSelection.dependencies(plan, fonts)
        assertTrue(supplements.size >= 150)
        supplements.forEach { info ->
            assertTrue(info.hidden); assertFalse(info.variable)
            assertNotNull(Font.Builder(info.file(store.directory)).build())
            assertTrue(store.license(info)!!.second.length > 1000)
        }
        fun shapedFiles(text: String, weight: Int = 400): Set<String> {
            val span = previewSpans(text, fonts, plan, store.directory, weight, "zh-Hans")
            val files = mutableSetOf<String>()
            TextShaper.shapeText(span, 0, span.length, TextDirectionHeuristics.FIRSTSTRONG_LTR,
                TextPaint().apply { textSize = 36f }) { _, _, glyphs, _ ->
                for (i in 0 until glyphs.glyphCount()) {
                    assertTrue("Missing glyph in $text", glyphs.getGlyphId(i) > 0)
                    files += glyphs.getFont(i).file!!.name
                }
            }
            return files
        }
        assertEquals(setOf(sarasa.fileName), shapedFiles("A中文"))
        assertEquals(setOf(family.single { it.weight == 700 }.fileName), shapedFiles("A中文", 700))
        listOf("سلام", "שָׁלוֹם", "क्षत्रिय", "བོད", "𞤀", "𒀀", "𑜒", "𗀀").forEach { text ->
            val files = shapedFiles(text)
            assertTrue("Use offline Noto for $text: $files", files.all { file -> supplements.any { it.fileName == file } })
        }
        assertTrue(shapedFiles("👨‍👩‍👧‍👦").any { it.contains("NotoColorEmoji") })
        val snapshot = DeviceRepository().inspect()
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val module = ModuleBuilder(context).build(snapshot, plan, fonts, store.directory)
        ZipFile(module.file).use { zip ->
            family.forEach { assertNotNull(zip.getEntry("system/fonts/GlyphOrder-${it.fileName}")) }
            assertNotNull(zip.getEntry("licenses/Sarasa-OFL.txt"))
            assertFalse(zip.entries().asSequence().any { it.name.endsWith("NotoColorEmoji.ttf") })
            val xml = zip.getInputStream(zip.getEntry("system/etc/font_fallback.xml")).bufferedReader().use { it.readText() }
            assertTrue(xml.contains("fallbackFor=\"sans-serif\"")); assertTrue(xml.contains("NotoColorEmoji.ttf"))
        }
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
    }
}

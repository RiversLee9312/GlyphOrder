package dev.glyphorder.app

import android.net.Uri
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import dev.glyphorder.app.ui.previewSpans
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()
    private val context get() = fixtures.context
    private fun uri(name: String) = Uri.parse("content://dev.glyphorder.testfonts/$name")

    @Test fun rtlAndIndicKeepShapingRunsAndEmojiClustersIntact() {
        val store = FontStore(context)
        val font = store.import(uri("Roboto.ttf"))
        listOf("السَّلَامُ عَلَيْكُمْ", "שָׁלוֹם עוֹלָם", "متن پیش‌نمایش فارسی", "क्षत्रिय श्री ज्ञ").forEach { text ->
            val span = previewSpans(text, listOf(font), FontPlan(primary = font.id), store.directory, 400, "ar")
            val faces = span.getSpans(0, text.length, android.text.style.TypefaceSpan::class.java)
            assertEquals("Preserve a continuous shaping run: $text", 1, faces.size)
            assertEquals(0, span.getSpanStart(faces[0])); assertEquals(text.length, span.getSpanEnd(faces[0]))
        }
        val text = "A 👨‍👩‍👧‍👦 🇨🇳 e\u0301 Z"
        val span = previewSpans(text, listOf(font), FontPlan(primary = font.id), store.directory, 400, "en")
        val boundaries = android.icu.text.BreakIterator.getCharacterInstance().apply { setText(text) }
        span.getSpans(0, text.length, android.text.style.TypefaceSpan::class.java).forEach {
            assertTrue(boundaries.isBoundary(span.getSpanStart(it)))
            assertTrue(boundaries.isBoundary(span.getSpanEnd(it)))
        }
        assertTrue(dev.glyphorder.app.ui.PreviewSamples.all.size >= 30)
    }

    @Test fun importedVariableStaticAndOpenTypeAreValidatedAndPreviewed() {
        val store = FontStore(context)
        val variable = store.import(uri("Roboto.ttf"))
        val static = store.import(uri("SourceSansPro.ttf"))
        val otf = store.import(uri("Ahom.otf"))
        assertTrue(variable.axes.any { it.tag == "wght" })
        assertTrue(static.axes.isEmpty())
        assertEquals("otf", otf.file(store.directory).extension)
        val before = store.directory.listFiles().orEmpty().map { it.name }.sorted()
        assertThrows(IllegalArgumentException::class.java) { store.import(uri("Invalid.ttf")) }
        assertEquals(before, store.directory.listFiles().orEmpty().map { it.name }.sorted())
        val text = "A 中文 한글 あ 𑜒 🙂 e\u0301 👨‍👩‍👧‍👦"
        val span = previewSpans(text, listOf(variable, static, otf), FontPlan(primary = variable.id,
            fallbacks = mapOf("und-Ahom" to otf.id), universal = listOf(static.id)), store.directory, 700, "zh-Hans")
        assertEquals(text, span.toString())
        assertTrue(span.getSpans(0, text.length, android.text.style.TypefaceSpan::class.java).isNotEmpty())
        assertTrue(ScriptCatalog.all.size >= 150)
        assertTrue(ScriptCatalog.all.any { it.tag == "und-Adlm" })
        assertTrue(ScriptCatalog.all.any { it.tag == "und-Ahom" })
    }

    @Test fun actualDeviceSnapshotProducesCompleteSystemlessZip() {
        val store = FontStore(context)
        val variable = store.import(uri("Roboto.ttf")); val static = store.import(uri("SourceSansPro.ttf")); val otf = store.import(uri("Ahom.otf"))
        val snapshot = DeviceRepository().inspect()
        val targets = setOf("sans-serif", "google-sans").intersect(snapshot.targets.toSet())
        assertTrue(snapshot.configs.containsKey("/system/etc/font_fallback.xml"))
        assertEquals("u:object_r:system_font_fallback_file:s0", snapshot.contexts["/system/etc/font_fallback.xml"])
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val plan = FontPlan(primary = variable.id, targets = targets, fallbacks = mapOf("und-Ahom" to otf.id), universal = listOf(static.id))
        val module = ModuleBuilder(context).build(snapshot, plan, listOf(variable, static, otf), store.directory)
        ZipFile(module.file).use { zip ->
            val paths = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(paths.containsAll(listOf("module.prop", "customize.sh", "service.sh", "system/etc/fonts.xml", "system/etc/font_fallback.xml", "baseline/system/etc/font_fallback.xml")))
            assertNotNull(zip.getEntry("zygisk/${android.os.Build.SUPPORTED_ABIS.first()}.so"))
            assertNotNull(zip.getEntry("licenses/Zygisk-API.txt"))
            val preloader = zip.getInputStream(zip.getEntry("zygisk/${android.os.Build.SUPPORTED_ABIS.first()}.so")).use { it.readBytes() }
            assertArrayEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46), preloader.take(4).toByteArray())
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                val expected = apk.getInputStream(apk.getEntry("lib/${android.os.Build.SUPPORTED_ABIS.first()}/libglyphorder_preload.so")).use { it.readBytes() }
                assertArrayEquals(expected, preloader)
            }
            assertFalse(paths.any { it.startsWith("/") || ".." in it.split('/') || it.endsWith("NotoColorEmoji.ttf") })
            assertNotNull(zip.getEntry("system/fonts/GlyphOrder-${variable.fileName}"))
            if ("google-sans" in targets) assertNotNull(zip.getEntry("system/product/fonts/GlyphOrder-${variable.fileName}"))
            fun text(path: String) = zip.getInputStream(zip.getEntry(path)).bufferedReader().use { it.readText() }
            assertTrue(text("module.prop").contains("version=${BuildConfig.VERSION_NAME}\n"))
            assertTrue(text("system/etc/font_fallback.xml").contains("supportedAxes"))
            assertFalse(text("system/etc/fonts.xml").contains("supportedAxes"))
            assertTrue(text("customize.sh").contains("system_font_fallback_file"))
            assertTrue(text("system/etc/font_fallback.xml").contains("NotoColorEmoji.ttf"))
            assertEquals(snapshot.configs.getValue("/system/etc/font_fallback.xml"), text("baseline/system/etc/font_fallback.xml"))
        }
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
    }

    /** Opt-in only. Stages a same-font module through Magisk and leaves it disabled. Never reboots. */
    @Test fun officialMagiskInstallAndDisable() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("installModule") == "true")
        val store = FontStore(context)
        val font = store.import(uri("Roboto.ttf"))
        val snapshot = DeviceRepository().inspect()
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val module = ModuleBuilder(context).build(snapshot, FontPlan(primary = font.id), listOf(font), store.directory)
        val log = ModuleBuilder(context).install(module)
        assertTrue(log.isNotBlank())
        val installed = DeviceRepository().inspect()
        assertTrue(installed.installed || installed.pending)
        val stage = if (Root.exists("/data/adb/modules_update/$MODULE_ID/module.prop")) "/data/adb/modules_update/$MODULE_ID" else "/data/adb/modules/$MODULE_ID"
        assertTrue(Root.run("ls -Zd '$stage/system/etc/font_fallback.xml'").contains("u:object_r:system_font_fallback_file:s0"))
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
        if (installed.installed) DeviceRepository().disable() else Root.run("touch '$stage/disable'")
        assertTrue(Root.exists("$stage/disable"))
    }

    /** Generates and inspects a ZIP only. Never installs a module or reboots. */
    @Test fun globalDefaultsFitEveryBuiltinAndImportedFontAndKeepRecoveryState() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val imported = listOf("Roboto.ttf", "SourceSansPro.ttf", "Ahom.otf").map { store.import(uri(it)) }
        val fonts = store.all()
        val builtins = fonts.filter { store.isBundled(it) && !it.hidden }
        assertEquals(5, builtins.size)
        val snapshot = DeviceRepository().inspect()
        assertTrue("System font cmap data must be available", snapshot.fontCoverage.size >= 100)
        snapshot.configs.forEach { (path, xml) ->
            val nodes = FontConfig.parse(xml).getElementsByTagName("font")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as org.w3c.dom.Element
                val name = node.textContent.trim()
                val filename = if (name.startsWith('/')) name else
                    (if (path.startsWith("/product/")) "/product/fonts/" else "/system/fonts/") + name
                if (java.io.File(filename).canRead()) assertTrue("Baseline face must be verified even with a module active: $filename",
                    "$filename#${node.getAttribute("index").ifEmpty { "0" }}" in snapshot.fontCoverage)
            }
        }
        println("Verified system faces: ${snapshot.fontCoverage.size}; baseline faces included during regeneration.")
        val before = CONFIG_PATHS.filter(Root::exists).associateWith(Root::read)
        val markers = listOf("/data/adb/modules/$MODULE_ID/disable", "/data/adb/modules_update/$MODULE_ID/disable")
        val disabled = markers.associateWith(Root::exists)
        (builtins + imported).forEach { primary ->
            val plan = FontPlan(primary = primary.id, global = true)
            assertTrue(FontSelection.dependencies(plan, fonts).size >= 167)
            val module = ModuleBuilder(context).build(snapshot, plan, fonts, store.directory)
            assertTrue(module.maxFamilies in 1..254)
            ModuleSafety.validateArchive(module.file, snapshot.configs.keys)
            ZipFile(module.file).use { zip ->
                snapshot.configs.keys.forEach { path ->
                    val entry = zip.getEntry(DeviceRepository.overlayPath(path))
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val families = FontConfig.parse(xml).getElementsByTagName("family")
                    for (i in 0 until families.length) {
                        val family = families.item(i) as org.w3c.dom.Element
                        if (family.hasAttribute("name") && !family.textContent.contains("NotoColorEmoji")) {
                            assertTrue("Global named family: ${family.getAttribute("name")}", family.textContent.contains("GlyphOrder-${primary.fileName}"))
                        }
                    }
                }
            }
            println("Global ${primary.name} ZIP: ${module.maxFamilies}/254 native families; ${module.file.length()} bytes.")
        }
        assertEquals(before, CONFIG_PATHS.filter(Root::exists).associateWith(Root::read))
        assertEquals(disabled, markers.associateWith(Root::exists))
        println("All default fallback modules passed. Live configuration and recovery markers unchanged.")
    }

    @Test fun importedFontUsesBundledDefaultFallbackInNativeShaping() {
        val store = FontStore(context)
        store.ensureBundledFonts()
        val primary = store.import(uri("SourceSansPro.ttf"))
        val fonts = store.all()
        val text = "中"
        assertFalse(FontSelection.covers(primary, text.codePoints().toArray()))
        val defaultPlan = FontPlan(primary = primary.id)
        val bundledPaths = FontSelection.dependencies(defaultPlan, fonts).map { it.file(store.directory).canonicalPath }.toSet()
        fun shaped(enabled: Boolean): android.graphics.text.PositionedGlyphs {
            val span = previewSpans(text, fonts, defaultPlan.copy(automaticFallback = enabled), store.directory, 400, "zh-Hans")
            val face = span.getSpans(0, text.length, android.text.style.TypefaceSpan::class.java).single().typeface
            val paint = Paint().apply { typeface = face; textSize = 48f }
            return android.graphics.text.TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, false, paint)
        }
        val automatic = shaped(true)
        assertTrue(automatic.glyphCount() > 0)
        for (i in 0 until automatic.glyphCount()) {
            assertTrue(automatic.getGlyphId(i) != 0)
            assertTrue("Native glyph must come from the app's bundled fallback", automatic.getFont(i).file?.canonicalPath.orEmpty() in bundledPaths)
        }
        val disabled = shaped(false)
        for (i in 0 until disabled.glyphCount()) assertFalse(disabled.getFont(i).file?.canonicalPath.orEmpty() in bundledPaths)
        println("Imported font native Chinese glyph source: ${automatic.getFont(0).file}; disabling automatic fallback uses system fonts.")
    }

    @Test fun defaultFallbackPreferencesAreBackwardCompatibleAndPersist() {
        val prefs = context.getSharedPreferences("fonts", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("plan", null)
        val store = FontStore(context)
        try {
            prefs.edit().putString("plan", """{"primary":"custom","global":true,"fallbacks":{"und-Arab":"manual"}}""").commit()
            val legacy = store.load()
            assertTrue(legacy.automaticFallback)
            assertEquals("manual", legacy.fallbacks["und-Arab"])
            store.save(legacy.copy(automaticFallback = false))
            assertFalse(store.load().automaticFallback)
            store.save(FontSelection.defaultFallbacks(store.load()))
            val restored = store.load()
            assertTrue(restored.automaticFallback)
            assertTrue(restored.global)
            assertEquals("custom", restored.primary)
            assertTrue(restored.fallbacks.isEmpty())
            assertTrue(restored.universal.isEmpty())
        } finally {
            prefs.edit().apply { if (previous == null) remove("plan") else putString("plan", previous) }.commit()
        }
    }
}

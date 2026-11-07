package dev.glyphorder.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.glyphorder.app.font.*
import dev.glyphorder.app.module.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class FontLibraryIntegrationTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()

    private fun archive(invalid: Boolean = false): File = File(fixtures.context.cacheDir, "Family.zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Family/Regular.ttf"))
            File("/system/fonts/NotoSerif-Regular.ttf").inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            zip.putNextEntry(ZipEntry("Family/Bold.ttf"))
            if (invalid) zip.write(byteArrayOf(1, 2, 3)) else File("/system/fonts/NotoSerif-Bold.ttf").inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    @Test fun zipImportGroupsWeightsAndFamilyEditsKeepTheSavedReference() {
        val store = FontStore(fixtures.context)
        assertEquals(2, store.importFamilies(listOf(Uri.fromFile(archive()))))
        var fonts = store.all()
        val family = store.library(fonts).single()
        assertEquals(2, family.members.size)
        assertEquals(setOf(400, 700), family.faces(fonts).map { it.weight }.toSet())
        store.renameFamily(family.id, "My family", fonts)
        assertEquals("My family", store.library(fonts).single().name)
        store.removeFamilyFace(family, family.faces(fonts).single { it.weight == 400 }, fonts)
        fonts = store.all()
        val retained = store.library(fonts).single()
        assertEquals(family.id, retained.id)
        assertEquals("My family", retained.name)
        val plan = FontCombination("c", "C", listOf(family.id)).preview(FontPlan(), listOf(retained), fonts)
        assertEquals(fonts.single().id, plan.primary)
        store.deleteFamily(retained, fonts)
        assertTrue(store.all().isEmpty())
        assertTrue(store.library(emptyList()).isEmpty())
    }

    @Test fun aBrokenMemberRollsBackTheWholeArchive() {
        val store = FontStore(fixtures.context)
        assertThrows(Exception::class.java) { store.importFamilies(listOf(Uri.fromFile(archive(invalid = true)))) }
        assertTrue(store.all().isEmpty())
        assertTrue(store.directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun builtInLanguageAndWeightFacesDoNotBecomeSeparateLibraryItems() {
        val store = FontStore(fixtures.context)
        store.ensureBundledFonts()
        val fonts = store.all(); val library = store.library(fonts)
        assertEquals(5, library.size)
        assertEquals(1, library.count { it.name == "MiSans" })
        assertTrue(library.single { it.name == "MiSans" }.members.size > 1)
        assertFalse(library.any { it.name.contains("Devanagari") })
    }

    @Test fun everyBuiltInFamilyFitsTheDeviceWithoutSelectingAnotherLibraryItem() {
        val store = FontStore(fixtures.context)
        store.ensureBundledFonts()
        val fonts = store.all(); val library = store.library(fonts)
        val snapshot = DeviceRepository().inspect()
        val before = snapshot.configs.keys.associateWith(Root::read)
        for (family in library) {
            val plan = FontCombination("test", "", listOf(family.id)).preview(FontPlan(global = true), library, fonts)
            val module = ModuleBuilder(fixtures.context).build(snapshot, plan, fonts, store.directory)
            ModuleSafety.validateArchive(module.file, snapshot.configs.keys)
            assertTrue("${family.name}: ${module.maxFamilies}", module.maxFamilies in 1..254)
            module.file.delete()
        }
        assertEquals(before, snapshot.configs.keys.associateWith(Root::read))
    }
}

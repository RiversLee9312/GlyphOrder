package dev.glyphorder.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.glyphorder.app.font.FontStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixtureIsolationTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()

    @Test fun importingTestSamplesCannotChangeTheUserLibraryOrPreferences() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val userStore = FontStore(real)
        val before = userStore.directory.listFiles().orEmpty().map { it.name }.sorted()
        val saved = real.getSharedPreferences("fonts", 0).getString("plan", null)
        val isolated = FontStore(fixtures.context)
        assertNotEquals(userStore.directory.canonicalPath, isolated.directory.canonicalPath)
        val fonts = listOf("Roboto.ttf", "SourceSansPro.ttf", "Ahom.otf").map {
            isolated.import(Uri.parse("content://dev.glyphorder.testfonts/$it"))
        }
        assertEquals(3, isolated.all().size)
        fonts.forEach { assertTrue(it.file(isolated.directory).isFile) }
        isolated.save(isolated.load().copy(primary = fonts.first().id))
        assertEquals(saved, real.getSharedPreferences("fonts", 0).getString("plan", null))
        assertEquals(before, userStore.directory.listFiles().orEmpty().map { it.name }.sorted())
        val localized = fixtures.context.createConfigurationContext(android.content.res.Configuration(real.resources.configuration))
        assertEquals(fixtures.context.filesDir.canonicalPath, localized.filesDir.canonicalPath)
    }
}

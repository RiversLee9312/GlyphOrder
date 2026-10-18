package dev.glyphorder.app

import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.LocaleList
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import dev.glyphorder.app.font.FontStore
import dev.glyphorder.app.font.FontPlan
import dev.glyphorder.app.module.*
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class LocaleAndIconTest {
    @get:org.junit.Rule val fixtures = FontFixtureStorage()
    @Test fun everyDeclaredLocaleResolvesResourcesAndRtl() {
        val context = fixtures.context
        val configId = context.resources.getIdentifier("locales_config", "xml", context.packageName)
        assertTrue(configId != 0)
        val locales = mutableListOf<String>()
        context.resources.getXml(configId).use { parser ->
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                    locales += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                }
                parser.next()
            }
        }
        assertTrue("At least 20 locales", locales.size >= 20)
        val strings = R.string::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }
        locales.forEach { tag ->
            val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            val localized = context.createConfigurationContext(configuration)
            strings.forEach { field -> assertTrue("$tag:${field.name}", localized.getString(field.getInt(null)).isNotEmpty()) }
            if (tag.startsWith("ar") || tag.startsWith("fa")) assertEquals(View.LAYOUT_DIRECTION_RTL, localized.resources.configuration.layoutDirection)
        }
    }
    @Test fun launcherProvidesMonochromeAdaptiveLayer() {
        val context = fixtures.context
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.foreground); assertNotNull(icon.background); assertNotNull(icon.monochrome)
    }

    @Test fun localizedInstallerShellsAndFormattedMessagesAreValid() {
        val context = fixtures.context
        val store = FontStore(context)
        val font = store.import(Uri.parse("content://dev.glyphorder.testfonts/Roboto.ttf"))
        val snapshot = DeviceRepository().inspect()
        val script = File(context.cacheDir, "installer-qa.sh")
        try {
            for (tag in listOf("en", "zh-CN", "zh-TW", "ja", "ko", "fr", "de", "es", "pt-BR", "it", "ru", "uk", "pl", "tr", "ar", "fa", "hi", "bn", "id", "vi", "th", "nl", "sv", "fil")) {
                val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
                val localized = context.createConfigurationContext(config)
                assertTrue(localized.getString(R.string.font_static_weight_detail, "400").isNotBlank())
                for (count in listOf(0, 1, 2, 3, 5, 11, 21, 101)) {
                    assertTrue(localized.resources.getQuantityString(R.plurals.notice_imported_fonts, count, count).isNotBlank())
                }
                val module = ModuleBuilder(localized).build(snapshot, FontPlan(primary = font.id), listOf(font), store.directory)
                ZipFile(module.file).use { zip -> script.writeBytes(zip.getInputStream(zip.getEntry("customize.sh")).use { it.readBytes() }) }
                Root.run("sh -n ${Root.quote(script.absolutePath)}")
            }
        } finally { script.delete() }
    }
}

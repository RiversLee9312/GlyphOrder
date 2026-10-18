package dev.glyphorder.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File

/** All imports, generated ZIPs and preferences stay outside the user's font store. */
class FontFixtureStorage : ExternalResource() {
    lateinit var context: FixtureContext
        private set
    override fun before() {
        context = FixtureContext(InstrumentationRegistry.getInstrumentation().targetContext)
    }
    override fun after() { context.cleanup() }
}

class FixtureContext private constructor(base: Context, private val storage: Storage) : ContextWrapper(base) {
    private class Storage(base: Context) {
        val prefix = "font-fixture-${System.nanoTime()}"
        val root = File(base.cacheDir, prefix).canonicalFile.apply {
            check(toPath().startsWith(base.cacheDir.canonicalFile.toPath()))
            check(mkdirs())
        }
        val preferences = mutableSetOf<String>()
    }
    constructor(base: Context) : this(base, Storage(base))
    override fun getFilesDir(): File = File(storage.root, "files").apply { mkdirs() }
    override fun getCacheDir(): File = File(storage.root, "cache").apply { mkdirs() }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolated = "${storage.prefix}-$name"
        storage.preferences += isolated
        return super.getSharedPreferences(isolated, mode)
    }
    override fun createConfigurationContext(configuration: Configuration): Context =
        FixtureContext(super.createConfigurationContext(configuration), storage)
    fun cleanup() {
        storage.preferences.forEach { super.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        check(storage.root.toPath().startsWith(baseContext.cacheDir.canonicalFile.toPath()))
        check(storage.root.deleteRecursively())
    }
}

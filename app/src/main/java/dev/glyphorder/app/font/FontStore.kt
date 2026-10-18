package dev.glyphorder.app.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.fonts.Font
import dev.glyphorder.app.UiError
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BundledFont(val name: String, val asset: String, val sha256: String, val license: String, val originalLicense: String? = null,
    val family: String? = null, val hidden: Boolean = false, val preset: String? = null, val fallbackGroup: String? = null,
    val tags: Set<String> = emptySet(), val coverage: List<IntRange> = emptyList())

data class FontPlan(
    val primary: String? = null,
    val fallbacks: Map<String, String> = emptyMap(),
    val targets: Set<String> = setOf("sans-serif"),
    val axes: Map<String, Map<String, Float>> = emptyMap(),
    val universal: List<String> = emptyList(),
    val global: Boolean = false,
    val automaticFallback: Boolean = true,
    val styleMode: Boolean = false,
    val styleFonts: Map<String, String> = emptyMap()
)

class FontStore(private val context: Context) {
    val directory = File(context.filesDir, "fonts").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("fonts", Context.MODE_PRIVATE)
    private val bundled: List<BundledFont> by lazy {
        val json = JSONArray(context.assets.open("bundled-fonts.json").bufferedReader().use { it.readText() })
        (0 until json.length()).map { i -> json.getJSONObject(i).let {
            fun optional(key: String) = it.optString(key).takeIf(String::isNotBlank)
            val tags = it.optJSONArray("tags")
            val ranges = it.optJSONArray("coverage")
            BundledFont(it.getString("name"), it.getString("asset"), it.getString("sha256"), it.getString("license"),
                optional("originalLicense"), optional("family"), it.optBoolean("hidden"), optional("preset"), optional("fallbackGroup"),
                tags?.let { a -> (0 until a.length()).map(a::getString).toSet() }.orEmpty(),
                ranges?.let { a -> (0 until a.length()).map { n -> a.getJSONArray(n).let { r -> r.getInt(0)..r.getInt(1) } } }.orEmpty())
        } }
    }
    fun ensureBundledFonts() {
        bundled.forEach { entry ->
            val target = File(directory, entry.sha256.take(16) + "." + entry.asset.substringAfterLast('.'))
            if (target.exists() && runCatching { SfntReader.read(target).id == entry.sha256 }.getOrDefault(false)) return@forEach
            val temporary = File(directory, "bundled-${System.nanoTime()}.tmp")
            try {
                context.assets.open(entry.asset).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                val info = SfntReader.read(temporary)
                check(info.id == entry.sha256) { UiError.key("error_font_integrity") }
                Font.Builder(temporary).build()
                temporary.copyTo(target, overwrite = true)
            } finally { temporary.delete() }
        }
    }
    fun isBundled(info: FontInfo) = bundled.any { it.sha256 == info.id }
    fun license(info: FontInfo): Pair<String, String>? = bundled.find { it.sha256 == info.id }?.let { entry ->
        val groups = setOf("noto", "sarasa", entry.preset)
        val entries = listOf(entry) + bundled.filter { it.fallbackGroup != null && it.fallbackGroup in groups }
        entry.license.substringAfterLast('/') to entries.map { it.license }.distinct().joinToString("\n\n") { path ->
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }
    fun licenseFiles(info: FontInfo): Map<String, ByteArray> = bundled.find { it.sha256 == info.id }?.let { entry ->
        listOfNotNull(entry.license, entry.originalLicense).associate { asset ->
            asset.substringAfterLast('/') to context.assets.open(asset).use { it.readBytes() }
        }
    }.orEmpty()
    fun all(): List<FontInfo> = directory.listFiles().orEmpty().filter { it.extension in listOf("ttf", "otf") }
        .mapNotNull { runCatching { SfntReader.read(it) }.getOrNull() }
        .map { info -> bundled.find { it.sha256 == info.id }?.let {
            check(it.coverage.isEmpty() || it.coverage == info.coverage) { UiError.key("error_font_integrity") }
            info.copy(name = it.name,
            family = it.family, hidden = it.hidden, preset = it.preset, fallbackGroup = it.fallbackGroup,
            fallbackTags = it.tags, coverage = it.coverage.ifEmpty { info.coverage }) } ?: info }
        .sortedWith(compareBy<FontInfo> { !isBundled(it) }.thenBy { it.name })

    fun import(uri: Uri): FontInfo {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty()
        require(name.substringAfterLast('.', "").lowercase() in listOf("ttf", "otf")) { UiError.key("error_font_extension") }
        val temporary = File(directory, "import-${System.nanoTime()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= SfntReader.MAX_BYTES) { UiError.key("error_font_too_large") }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error(UiError.key("error_file_read"))
            val info = SfntReader.read(temporary)
            Font.Builder(temporary).build() // Let the platform reject fonts it cannot render.
            val target = info.file(directory)
            if (!target.exists()) check(temporary.renameTo(target)) { UiError.key("error_font_save") }
            return info
        } finally { temporary.delete() }
    }

    fun delete(info: FontInfo) {
        check(!isBundled(info)) { UiError.key("error_font_remove") }
        check(info.file(directory).delete()) { UiError.key("error_font_remove") }
    }
    fun save(plan: FontPlan) {
        val json = JSONObject().put("primary", plan.primary).put("targets", JSONArray(plan.targets.toList()))
            .put("fallbacks", JSONObject(plan.fallbacks)).put("universal", JSONArray(plan.universal)).put("global", plan.global)
            .put("automaticFallback", plan.automaticFallback).put("styleMode", plan.styleMode)
            .put("styleFonts", JSONObject(plan.styleFonts)).put("styleGroupsVersion", 2)
        val axes = JSONObject()
        plan.axes.forEach { (id, values) -> axes.put(id, JSONObject(values)) }
        preferences.edit().putString("plan", json.put("axes", axes).toString()).apply()
    }
    fun load(): FontPlan = runCatching {
        val json = JSONObject(preferences.getString("plan", "{}")!!)
        val fallbacks = json.optJSONObject("fallbacks") ?: JSONObject()
        val axes = json.optJSONObject("axes") ?: JSONObject()
        val styles = json.optJSONObject("styleFonts")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
        } ?: mutableMapOf()
        // Older mixed plans grouped casual/cursive under Other. Preserve their
        // choices once, then let the two styles be cleared/changed independently.
        if (json.optInt("styleGroupsVersion", 1) < 2) styles["other"]?.let { id ->
            styles.putIfAbsent("casual", id); styles.putIfAbsent("cursive", id)
        }
        FontPlan(json.optString("primary").takeIf { it.isNotBlank() && it != "null" },
            fallbacks.keys().asSequence().associateWith { fallbacks.getString(it) },
            json.optJSONArray("targets")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: setOf("sans-serif"),
            axes.keys().asSequence().associateWith { id ->
                val obj = axes.getJSONObject(id)
                obj.keys().asSequence().associateWith { obj.getDouble(it).toFloat() }
            }, json.optJSONArray("universal")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty(), json.optBoolean("global"),
            json.optBoolean("automaticFallback", true), json.optBoolean("styleMode"),
            styles)
    }.getOrDefault(FontPlan())
}

package dev.glyphorder.app.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.fonts.Font
import dev.glyphorder.app.UiError
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

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
    val styleFonts: Map<String, String> = emptyMap(),
    val styleChains: Map<String, List<String>> = emptyMap(),
    val orderedFallbacks: List<String> = emptyList(),
    val combinationId: String? = null,
    val styleCombinations: Map<String, String> = emptyMap(),
    val familyComponents: Map<String, List<String>> = emptyMap()
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

    fun saveLibrary(library: List<FontLibraryFamily>) {
        val json = JSONArray().apply { library.forEach { family -> put(JSONObject()
            .put("id", family.id).put("name", family.name).put("members", JSONArray(family.members)).put("bundled", family.bundled)) } }
        preferences.edit().putString("library", json.toString()).apply()
    }

    fun library(fonts: List<FontInfo>): List<FontLibraryFamily> {
        val recorded = JSONArray(preferences.getString("library", "[]")!!).let { array ->
            (0 until array.length()).map { i -> array.getJSONObject(i).let { item ->
                val members = item.getJSONArray("members")
                FontLibraryFamily(item.getString("id"), item.getString("name"),
                    (0 until members.length()).map(members::getString).filter { id -> fonts.any { it.id == id } }, item.getBoolean("bundled"))
            } }.filter { it.members.isNotEmpty() }
        }
        val builtins = FontLibrary.defaults(fonts.filter(::isBundled), ::isBundled).map { family ->
            family.copy(name = recorded.find { it.id == family.id }?.name ?: family.name)
        }
        val imported = recorded.filterNot { it.bundled }
        val known = imported.flatMap { it.members }.toSet()
        val unrecorded = FontLibrary.defaults(fonts.filter { !isBundled(it) && it.id !in known }) { false }
        return (builtins + imported + unrecorded).also { if (it != recorded) saveLibrary(it) }
    }

    fun renameFamily(id: String, name: String, fonts: List<FontInfo>) =
        saveLibrary(library(fonts).map { if (it.id == id) it.copy(name = name) else it })

    /** Stage and validate an entire import before touching the library or existing files. */
    fun importFamilies(uris: List<Uri>, targetFamily: String? = null): Int {
        val before = all()
        val catalog = library(before).toMutableList()
        if (targetFamily != null) require(catalog.any { it.id == targetFamily && !it.bundled }) { UiError.key("error_font_remove") }
        val stage = File(context.cacheDir, "font-import-${System.nanoTime()}").apply { check(mkdirs()) }
        val staged = linkedMapOf<String, Pair<FontInfo, File>>()
        var total = 0L
        fun receive(input: InputStream) {
            require(staged.size < 256) { UiError.key("error_total_fonts_large") }
            val file = File(stage, "face-${System.nanoTime()}.tmp")
            file.outputStream().use { output ->
                val buffer = ByteArray(65536); var size = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    size += n; total += n
                    require(size <= SfntReader.MAX_BYTES && total <= 384L * 1024 * 1024) { UiError.key("error_total_fonts_large") }
                    output.write(buffer, 0, n)
                }
            }
            val font = SfntReader.read(file)
            Font.Builder(file).build()
            if (font.id in staged) file.delete() else staged[font.id] = font to file
        }
        val committed = mutableListOf<File>()
        try {
            uris.forEach { uri ->
                val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } }.getOrNull() ?: uri.lastPathSegment.orEmpty()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    when (name.substringAfterLast('.', "").lowercase()) {
                        "ttf", "otf" -> receive(input)
                        "zip" -> ZipInputStream(input).use { zip ->
                            var entries = 0
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                require(++entries <= 2048) { UiError.key("error_total_fonts_large") }
                                if (!entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")) receive(zip)
                                else {
                                    val buffer = ByteArray(65536)
                                    while (true) {
                                        val n = zip.read(buffer); if (n < 0) break
                                        total += n
                                        require(total <= 384L * 1024 * 1024) { UiError.key("error_total_fonts_large") }
                                    }
                                }
                                zip.closeEntry()
                            }
                        }
                        else -> error(UiError.key("error_font_extension"))
                    }
                } ?: error(UiError.key("error_file_read"))
            }
            require(staged.isNotEmpty()) { UiError.key("error_font_extension") }
            val combined = (before + staged.values.map { it.first }).distinctBy { it.id }
            val affected = staged.values.map { it.first.family ?: it.first.id }.toSet()
            FontLibrary.validateFaces(combined.filterNot(::isBundled).filter { (it.family ?: it.id) in affected })
            staged.values.forEach { (font, file) ->
                if (!font.file(directory).exists()) { check(file.renameTo(font.file(directory))) { UiError.key("error_font_save") }; committed += font.file(directory) }
            }
            val incoming = staged.values.map { it.first }.filterNot(::isBundled)
            if (targetFamily != null) {
                require(catalog.none { it.id != targetFamily && it.members.any { member -> incoming.any { it.id == member } } }) {
                    UiError.key("error_font_family_conflict")
                }
                val index = catalog.indexOfFirst { it.id == targetFamily }
                catalog[index] = catalog[index].copy(members = (catalog[index].members + incoming.map { it.id }).distinct())
            } else incoming.groupBy { it.family ?: it.id }.forEach { (key, faces) ->
                val index = catalog.indexOfFirst { family -> !family.bundled && family.faces(combined).any { (it.family ?: it.id) == key } }
                if (index >= 0) catalog[index] = catalog[index].copy(members = (catalog[index].members + faces.map { it.id }).distinct())
                else catalog += FontLibrary.defaults(faces) { false }.single()
            }
            saveLibrary(catalog)
            return staged.size
        } catch (e: Exception) {
            committed.forEach(File::delete)
            throw e
        } finally {
            // This directory is created inside our cache, never an archive-provided path.
            stage.listFiles().orEmpty().forEach(File::delete); stage.delete()
        }
    }

    fun deleteFamily(family: FontLibraryFamily, fonts: List<FontInfo>) {
        check(!family.bundled) { UiError.key("error_font_remove") }
        family.faces(fonts).forEach(::delete)
        saveLibrary(library(fonts).filterNot { it.id == family.id })
    }

    fun removeFamilyFace(family: FontLibraryFamily, face: FontInfo, fonts: List<FontInfo>) {
        check(!family.bundled && family.members.size > 1 && face.id in family.members) { UiError.key("error_font_remove") }
        delete(face)
        saveLibrary(library(fonts).map { if (it.id == family.id) it.copy(members = it.members - face.id) else it })
    }
    private fun serialize(plan: FontPlan): JSONObject {
        val json = JSONObject().put("primary", plan.primary).put("targets", JSONArray(plan.targets.toList()))
            .put("fallbacks", JSONObject(plan.fallbacks)).put("universal", JSONArray(plan.universal)).put("global", plan.global)
            .put("automaticFallback", plan.automaticFallback).put("styleMode", plan.styleMode)
            .put("styleFonts", JSONObject(plan.styleFonts)).put("styleGroupsVersion", 2)
        val chains = JSONObject()
        plan.styleChains.forEach { (group, ids) -> chains.put(group, JSONArray(ids)) }
        json.put("styleChains", chains)
        json.put("combinationId", plan.combinationId).put("styleCombinations", JSONObject(plan.styleCombinations))
        val axes = JSONObject()
        plan.axes.forEach { (id, values) -> axes.put(id, JSONObject(values)) }
        return json.put("axes", axes)
    }
    fun save(plan: FontPlan) { preferences.edit().putString("plan", serialize(plan).toString()).apply() }

    private fun serialize(combinations: List<FontCombination>) = JSONArray().apply {
        combinations.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("fonts", JSONArray(it.fonts))) }
    }
    fun saveCombinations(combinations: List<FontCombination>) {
        preferences.edit().putString("combinations", serialize(combinations).toString()).apply()
    }
    fun combinations(fonts: List<FontInfo>): Pair<FontPlan, List<FontCombination>> {
        val plan = load()
        if (!preferences.contains("combinations")) {
            val migrated = CombinationPlan.migrate(plan, fonts)
            preferences.edit().putString("plan", serialize(migrated.first).toString())
                .putString("combinations", serialize(migrated.second).toString()).apply()
            return migrated
        }
        val catalog = JSONArray(preferences.getString("combinations", "[]")!!)
        return plan to (0 until catalog.length()).map { i ->
            val item = catalog.getJSONObject(i); val ids = item.getJSONArray("fonts")
            FontCombination(item.getString("id"), item.getString("name"), (0 until ids.length()).map(ids::getString).distinct())
        }
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
        val chains = json.optJSONObject("styleChains")?.let { obj ->
            obj.keys().asSequence().filter { it in FontStyles.groups }.associateWith { group ->
                val ids = obj.getJSONArray(group)
                (0 until ids.length()).map(ids::getString).distinct()
            }
        } ?: styles.mapValues { listOf(it.value) }
        FontPlan(json.optString("primary").takeIf { it.isNotBlank() && it != "null" },
            fallbacks.keys().asSequence().associateWith { fallbacks.getString(it) },
            json.optJSONArray("targets")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: setOf("sans-serif"),
            axes.keys().asSequence().associateWith { id ->
                val obj = axes.getJSONObject(id)
                obj.keys().asSequence().associateWith { obj.getDouble(it).toFloat() }
            }, json.optJSONArray("universal")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty(), json.optBoolean("global"),
            json.optBoolean("automaticFallback", true), json.optBoolean("styleMode"),
            styles, chains, emptyList(), json.optString("combinationId").takeIf { it.isNotBlank() && it != "null" },
            json.optJSONObject("styleCombinations")?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }.orEmpty())
    }.getOrDefault(FontPlan())
}

package dev.glyphorder.app.font

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import dev.glyphorder.app.UiError

data class FontAxis(val tag: String, val min: Float, val default: Float, val max: Float)
data class FontInfo(
    val id: String, val fileName: String, val name: String, val postScriptName: String,
    val weight: Int, val italic: Boolean, val axes: List<FontAxis>, val bytes: Long,
    val scripts: Set<String>,
    val family: String? = null, val hidden: Boolean = false,
    val preset: String? = null, val fallbackGroup: String? = null,
    val fallbackTags: Set<String> = emptySet(), val coverage: List<IntRange> = emptyList()
) {
    val variable get() = axes.isNotEmpty()
    fun file(directory: File) = File(directory, fileName)
}

/** Bounded SFNT reader. We accept single-face TTF/OTF, never collections or web fonts. */
object SfntReader {
    const val MAX_BYTES = 128L * 1024 * 1024
    fun read(file: File): FontInfo {
        require(file.length() in 12..MAX_BYTES) { UiError.key("error_font_size_invalid") }
        RandomAccessFile(file, "r").use { raf ->
            val signature = raf.readInt()
            require(signature == 0x00010000 || signature == 0x4F54544F) { UiError.key("error_font_single_face") }
            val count = raf.readUnsignedShort()
            require(count in 1..256 && 12L + count * 16L <= raf.length()) { UiError.key("error_font_directory_corrupt") }
            raf.skipBytes(6)
            val tables = mutableMapOf<String, Pair<Long, Int>>()
            repeat(count) {
                val tag = ByteArray(4).also(raf::readFully).toString(Charsets.ISO_8859_1)
                raf.skipBytes(4)
                val offset = raf.readInt().toLong() and 0xffffffffL
                val length = raf.readInt().toLong() and 0xffffffffL
                require(offset + length <= raf.length() && length <= MAX_BYTES) { UiError.key("error_font_table_bounds") }
                tables[tag] = offset to length.toInt()
            }
            require(listOf("head", "name", "cmap").all(tables::containsKey)) { UiError.key("error_font_required_data") }
            fun table(tag: String): ByteBuffer? = tables[tag]?.let { (o, n) ->
                require(n <= 16 * 1024 * 1024) { UiError.key("error_font_metadata_large") }
                raf.seek(o)
                ByteBuffer.wrap(ByteArray(n).also(raf::readFully)).order(ByteOrder.BIG_ENDIAN)
            }
            val nameTable = table("name")!!
            require(nameTable.limit() >= 6) { UiError.key("error_font_name_table") }
            fun u16(b: ByteBuffer, pos: Int) = b.getShort(pos).toInt() and 0xffff
            val names = mutableMapOf<Int, Pair<Int, String>>()
            val nameCount = u16(nameTable, 2)
            val stringOffset = u16(nameTable, 4)
            require(6L + nameCount * 12L <= nameTable.limit()) { UiError.key("error_font_name_bounds") }
            repeat(nameCount) { i ->
                val p = 6 + i * 12
                val platform = u16(nameTable, p)
                val language = u16(nameTable, p + 4)
                val id = u16(nameTable, p + 6)
                val size = u16(nameTable, p + 8)
                val offset = stringOffset + u16(nameTable, p + 10)
                if (offset + size <= nameTable.limit() && platform in 0..3) {
                    val raw = ByteArray(size) { nameTable.get(offset + it) }
                    val text = raw.toString(if (platform == 0 || platform == 3) Charsets.UTF_16BE else Charsets.ISO_8859_1)
                        .replace(Regex("[\\p{Cntrl}]"), "").trim().take(150)
                    val score = (if (platform == 3) 4 else 0) + (if (language == 0x409) 2 else 0)
                    if (text.isNotEmpty() && score >= (names[id]?.first ?: -1)) names[id] = score to text
                }
            }
            val axes = mutableListOf<FontAxis>()
            table("fvar")?.let { b ->
                require(b.limit() >= 16) { UiError.key("error_font_axis_table") }
                val start = u16(b, 4); val n = u16(b, 8); val size = u16(b, 10)
                require(n <= 64 && size >= 20 && start.toLong() + n.toLong() * size <= b.limit()) { UiError.key("error_font_axes_bounds") }
                repeat(n) { i ->
                    val p = start + i * size
                    val tag = ByteArray(4) { b.get(p + it) }.toString(Charsets.ISO_8859_1)
                    require(tag.matches(Regex("[A-Za-z0-9 ]{4}"))) { UiError.key("error_font_axis_tag") }
                    val min = b.getInt(p + 4) / 65536f
                    val default = b.getInt(p + 8) / 65536f
                    val max = b.getInt(p + 12) / 65536f
                    require(min <= default && default <= max) { UiError.key("error_font_axis_range") }
                    axes += FontAxis(tag, min, default, max)
                }
            }
            val os2 = table("OS/2")
            val weight = if (os2 != null && os2.limit() >= 8) u16(os2, 4).coerceIn(1, 1000) else 400
            val italic = os2 != null && os2.limit() >= 64 && (u16(os2, 62) and 1 != 0)
            val hash = file.inputStream().use { stream ->
                val md = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            val scripts = mutableSetOf<String>()
            val cmap = table("cmap")!!
            if (cmap.limit() >= 4) {
                val n = u16(cmap, 2)
                require(4L + n * 8L <= cmap.limit()) { UiError.key("error_font_cmap") }
                fun hasGlyph(cp: Int): Boolean {
                    repeat(n) { i ->
                        val off = cmap.getInt(8 + i * 8).toLong() and 0xffffffffL
                        if (off + 2 > cmap.limit()) return@repeat
                        val o = off.toInt()
                        when (u16(cmap, o)) {
                            12 -> if (o + 16 <= cmap.limit()) {
                                val groups = cmap.getInt(o + 12)
                                if (groups in 0..100000 && o + 16L + groups * 12L <= cmap.limit()) {
                                    for (g in 0 until groups) {
                                        val p = o + 16 + g * 12
                                        val start = cmap.getInt(p); val end = cmap.getInt(p + 4)
                                        if (cp in start..end && cmap.getInt(p + 8).toLong() + cp - start > 0) return true
                                    }
                                }
                            }
                            4 -> if (cp <= 0xffff && o + 14 <= cmap.limit()) {
                                val segments = u16(cmap, o + 6) / 2
                                if (segments in 1..8192 && o + 16L + segments * 8L <= cmap.limit()) {
                                    for (s in 0 until segments) {
                                        val end = u16(cmap, o + 14 + 2 * s)
                                        val start = u16(cmap, o + 16 + 2 * segments + 2 * s)
                                        if (cp in start..end) {
                                            val delta = cmap.getShort(o + 16 + 4 * segments + 2 * s).toInt()
                                            val rp = o + 16 + 6 * segments + 2 * s
                                            val range = u16(cmap, rp)
                                            if (range == 0) return (cp + delta) and 0xffff != 0
                                            val gp = rp + range + 2 * (cp - start)
                                            if (gp + 2 <= cmap.limit()) {
                                                val glyph = u16(cmap, gp)
                                                if (glyph != 0 && (glyph + delta) and 0xffff != 0) return true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return false
                }
                listOf("Latn" to 0x41, "Grek" to 0x391, "Cyrl" to 0x410,
                    "Hebr" to 0x5d0, "Arab" to 0x628, "Hani" to 0x4e2d,
                    "Hira" to 0x3042, "Kana" to 0x30a2, "Hang" to 0xac00)
                    .forEach { (s, cp) -> if (hasGlyph(cp)) scripts += s }
            }
            return FontInfo(hash, hash.take(16) + if (signature == 0x4F54544F) ".otf" else ".ttf",
                names[4]?.second ?: names[16]?.second ?: names[1]?.second ?: file.nameWithoutExtension,
                names[6]?.second ?: "", weight, italic, axes, file.length(), scripts,
                coverage = CmapCoverage.readCmap(cmap))
        }
    }
}

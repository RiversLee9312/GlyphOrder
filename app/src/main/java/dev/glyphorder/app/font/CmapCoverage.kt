package dev.glyphorder.app.font

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.BitSet

/** Unicode cmap coverage, including TTC faces and supplementary planes. No name heuristics. */
object CmapCoverage {
    fun union(ranges: Iterable<List<IntRange>>): List<IntRange> {
        val bits = BitSet()
        ranges.forEach { list -> list.forEach { bits.set(it.first, it.last + 1) } }
        return ranges(bits)
    }

    fun contains(available: List<IntRange>, required: List<IntRange>): Boolean {
        var index = 0
        return required.all { range ->
            while (index < available.size && available[index].last < range.first) index++
            index < available.size && available[index].first <= range.first && available[index].last >= range.last
        }
    }

    fun intersection(ranges: List<List<IntRange>>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val bits = BitSet()
        ranges.first().forEach { bits.set(it.first, it.last + 1) }
        ranges.drop(1).forEach { list ->
            val other = BitSet()
            list.forEach { other.set(it.first, it.last + 1) }
            bits.and(other)
        }
        return ranges(bits)
    }

    private fun ranges(bits: BitSet): List<IntRange> = buildList {
        var start = bits.nextSetBit(0)
        while (start >= 0) {
            val end = bits.nextClearBit(start)
            add(start until end)
            start = bits.nextSetBit(end)
        }
    }

    fun read(source: ByteBuffer, index: Int = 0): List<IntRange> {
        val b = source.duplicate().order(ByteOrder.BIG_ENDIAN)
        fun u16(p: Int) = b.getShort(p).toInt() and 0xffff
        fun checked(p: Int, length: Long) { require(p >= 0 && length >= 0 && p.toLong() + length <= b.limit()) }
        checked(0, 12)
        val offset = if (b.getInt(0) == 0x74746366) {
            val count = b.getInt(8)
            require(count in 1..256 && index in 0 until count)
            checked(12, count * 4L)
            b.getInt(12 + index * 4)
        } else { require(index == 0); 0 }
        checked(offset, 12)
        require(b.getInt(offset) in setOf(0x00010000, 0x4f54544f))
        val count = u16(offset + 4)
        require(count in 1..256)
        checked(offset + 12, count * 16L)
        fun table(tag: Int): Pair<Int, Int>? = (0 until count).firstNotNullOfOrNull { n ->
            val entry = offset + 12 + n * 16
            if (b.getInt(entry) != tag) null else {
                val p = b.getInt(entry + 8); val length = b.getInt(entry + 12)
                checked(p, length.toLong()); p to length
            }
        }
        val (cmap, length) = requireNotNull(table(0x636d6170))
        require(length in 4..(16 * 1024 * 1024))
        val maxGlyphs = table(0x6d617870)?.let { (p, size) -> require(size >= 6); u16(p + 4) } ?: 65536
        val view = b.duplicate().apply { position(cmap); limit(cmap + length) }.slice().order(ByteOrder.BIG_ENDIAN)
        return readCmap(view, maxGlyphs)
    }

    fun readCmap(source: ByteBuffer, maxGlyphs: Int = 65536): List<IntRange> {
        val b = source.duplicate().order(ByteOrder.BIG_ENDIAN)
        fun u16(p: Int) = b.getShort(p).toInt() and 0xffff
        fun checked(p: Int, length: Long) { require(p >= 0 && length >= 0 && p.toLong() + length <= b.limit()) }
        checked(0, 4)
        val count = u16(2)
        checked(4, count * 8L)
        val preference = listOf(3 to 10, 0 to 6, 0 to 4, 3 to 1, 0 to 3, 0 to 2, 0 to 1, 0 to 0)
        val chosen = (0 until count).mapNotNull { n ->
            val entry = 4 + n * 8
            val rank = preference.indexOf(u16(entry) to u16(entry + 2))
            if (rank < 0) null else {
                val p = b.getInt(entry + 4); checked(p, 2)
                if (u16(p) in setOf(4, 12, 13)) rank to p else null
            }
        }.minByOrNull { it.first }?.second ?: return emptyList()
        val bits = BitSet()
        when (u16(chosen)) {
            4 -> {
                checked(chosen, 16)
                val length = u16(chosen + 2); val segments = u16(chosen + 6) / 2
                require(segments in 1..8192 && 16L + segments * 8L <= length)
                checked(chosen, length.toLong())
                var previousEnd = -1
                for (s in 0 until segments) {
                    val end = u16(chosen + 14 + s * 2)
                    val start = u16(chosen + 16 + segments * 2 + s * 2)
                    require(start <= end && start > previousEnd)
                    previousEnd = end
                    val delta = b.getShort(chosen + 16 + segments * 4 + s * 2).toInt()
                    val rangePos = chosen + 16 + segments * 6 + s * 2
                    val range = u16(rangePos)
                    for (cp in start..end) {
                        val glyph = if (range == 0) (cp + delta) and 0xffff else {
                            val p = rangePos + range + (cp - start) * 2
                            require(p >= chosen && p + 2 <= chosen + length)
                            val raw = u16(p)
                            if (raw == 0) 0 else (raw + delta) and 0xffff
                        }
                        if (glyph in 1 until maxGlyphs && cp != 0xffff) bits.set(cp)
                    }
                }
            }
            12, 13 -> {
                checked(chosen, 16)
                val format = u16(chosen); val length = b.getInt(chosen + 4); val groups = b.getInt(chosen + 12)
                require(groups in 0..100000 && 16L + groups * 12L <= length)
                checked(chosen, length.toLong())
                var previousEnd = -1
                for (n in 0 until groups) {
                    val p = chosen + 16 + n * 12
                    val start = b.getInt(p); val end = b.getInt(p + 4); val glyph = b.getInt(p + 8)
                    require(start in 0..0x10ffff && end in start..0x10ffff && glyph >= 0 && start > previousEnd)
                    previousEnd = end
                    if (format == 13) {
                        if (glyph in 1 until maxGlyphs) bits.set(start, end + 1)
                    } else {
                        val validStart = maxOf(start.toLong(), start.toLong() + 1 - glyph)
                        val validEnd = minOf(end.toLong(), start.toLong() + maxGlyphs - 1 - glyph)
                        if (validStart <= validEnd) bits.set(validStart.toInt(), validEnd.toInt() + 1)
                    }
                }
            }
        }
        return ranges(bits)
    }
}

package dev.glyphorder.app.font

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CmapCoverageTest {
    private fun cmap(format: Int = 12, start: Int = 0x10000, end: Int = 0x10002, glyph: Int = 1): ByteBuffer =
        ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1); putShort(3); putShort(10); putInt(12)
            putShort(format.toShort()); putShort(0); putInt(28); putInt(0); putInt(1)
            putInt(start); putInt(end); putInt(glyph); rewind()
        }

    @Test fun readsSupplementaryPlanesAndExcludesNotdefAndInvalidGlyphIds() {
        assertEquals(listOf(0x10000..0x10002), CmapCoverage.readCmap(cmap()))
        assertEquals(listOf(0x10001..0x10002), CmapCoverage.readCmap(cmap(glyph = 0)))
        assertEquals(listOf(0x10000..0x10001), CmapCoverage.readCmap(cmap(), maxGlyphs = 3))
        assertEquals(emptyList<IntRange>(), CmapCoverage.readCmap(cmap(format = 13, glyph = 0)))
        assertEquals(listOf(0x10000..0x10002), CmapCoverage.readCmap(cmap(format = 13)))
    }

    @Test fun readsBmpFormat4WithDeltaAndRangeOffsets() {
        val b = ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1); putShort(3); putShort(1); putInt(12)
            putShort(4); putShort(36); putShort(0); putShort(4); putShort(4); putShort(1); putShort(0)
            putShort(66); putShort(0xffff.toShort()); putShort(0)
            putShort(65); putShort(0xffff.toShort()); putShort(0); putShort(1)
            putShort(4); putShort(0); putShort(1); putShort(0); rewind()
        }
        assertEquals(listOf(65..65), CmapCoverage.readCmap(b))
    }

    @Test fun readsTtcAbsoluteTableOffsetsAndChecksFaceIndex() {
        val payload = cmap().array()
        val b = ByteBuffer.allocate(16 + 28 + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x74746366); putInt(0x10000); putInt(1); putInt(16)
            putInt(0x10000); putShort(1); put(ByteArray(6))
            putInt(0x636d6170); putInt(0); putInt(44); putInt(payload.size); put(payload); rewind()
        }
        assertEquals(listOf(0x10000..0x10002), CmapCoverage.read(b))
        assertThrows(IllegalArgumentException::class.java) { CmapCoverage.read(b, 1) }
        assertEquals(0, b.position())
    }

    @Test fun rejectsOutOfBoundsGroupsAndCodepoints() {
        val broken = cmap(); broken.putInt(24, 1000)
        assertThrows(IllegalArgumentException::class.java) { CmapCoverage.readCmap(broken) }
        assertThrows(IllegalArgumentException::class.java) { CmapCoverage.readCmap(cmap(end = 0x110000)) }
    }

    @Test fun unionIntersectionAndContainmentKeepGaps() {
        val merged = CmapCoverage.union(listOf(listOf(1..3, 8..9), listOf(4..5)))
        assertEquals(listOf(1..5, 8..9), merged)
        assertTrue(CmapCoverage.contains(merged, listOf(2..4, 9..9)))
        assertFalse(CmapCoverage.contains(merged, listOf(5..8)))
        assertEquals(listOf(3..5, 8..8), CmapCoverage.intersection(listOf(merged, listOf(3..8))))
    }
}

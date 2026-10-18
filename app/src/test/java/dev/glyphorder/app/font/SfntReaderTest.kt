package dev.glyphorder.app.font

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SfntReaderTest {
    private fun file(bytes: ByteArray, block: (File) -> Unit) {
        val path = File.createTempFile("font-test-", ".ttf")
        try { path.writeBytes(bytes); block(path) } finally { path.delete() }
    }
    private fun sfnt(axes: Boolean = false): ByteArray {
        val label = "Test Font".toByteArray(Charsets.UTF_16BE)
        val name = ByteBuffer.allocate(18 + label.size).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0); putShort(1); putShort(18)
            putShort(3); putShort(1); putShort(0x409); putShort(4); putShort(label.size.toShort()); putShort(0); put(label)
        }.array()
        val tables = linkedMapOf("head" to ByteArray(54), "name" to name, "cmap" to byteArrayOf(0, 0, 0, 0))
        if (axes) tables["fvar"] = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x00010000); putShort(16); putShort(2); putShort(1); putShort(20); putShort(0); putShort(0)
            put("wght".toByteArray()); putInt(100 * 65536); putInt(400 * 65536); putInt(900 * 65536); putShort(0); putShort(256)
        }.array()
        val directorySize = 12 + tables.size * 16
        val buffer = ByteBuffer.allocate(directorySize + tables.values.sumOf { it.size }).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x00010000); buffer.putShort(tables.size.toShort()); buffer.put(ByteArray(6))
        var offset = directorySize
        tables.forEach { (tag, bytes) -> buffer.put(tag.toByteArray()); buffer.putInt(0); buffer.putInt(offset); buffer.putInt(bytes.size); offset += bytes.size }
        tables.values.forEach(buffer::put)
        return buffer.array()
    }
    @Test fun readsNamesAndVariableAxisBounds() = file(sfnt(true)) { file ->
        val info = SfntReader.read(file)
        assertEquals("Test Font", info.name)
        assertEquals(listOf(FontAxis("wght", 100f, 400f, 900f)), info.axes)
        assertTrue(info.fileName.matches(Regex("[0-9a-f]{16}\\.ttf")))
    }
    @Test fun rejectsTruncatedFiles() = file(byteArrayOf(0, 1, 0)) {
        assertThrows(IllegalArgumentException::class.java) { SfntReader.read(it) }
    }
    @Test fun rejectsOutOfBoundsTableOffset() {
        val data = sfnt()
        ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(20, Int.MAX_VALUE)
        file(data) { assertThrows(IllegalArgumentException::class.java) { SfntReader.read(it) } }
    }
    @Test fun rejectsCollectionEvenWhenRenamedTtf() {
        val data = sfnt(); ByteBuffer.wrap(data).putInt(0, 0x74746366)
        file(data) { assertThrows(IllegalArgumentException::class.java) { SfntReader.read(it) } }
    }
    @Test fun rejectsInvertedVariableAxisRange() {
        val data = sfnt(true)
        ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(data.size - 16, 1000 * 65536)
        file(data) { assertThrows(IllegalArgumentException::class.java) { SfntReader.read(it) } }
    }
}

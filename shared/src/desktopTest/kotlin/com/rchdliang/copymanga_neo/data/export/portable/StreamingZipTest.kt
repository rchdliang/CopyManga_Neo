package com.rchdliang.copymanga_neo.data.export.portable

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StreamingZipTest {
    @Test fun storedEntriesAreReadableBySequentialZipReaderWithExactUnicodeNamesAndBytes() = runTest {
        val contents = linkedMapOf("中文/🌸/00001.png" to ByteArray(160_000) { (it * 17).toByte() }, "manifest.json" to "{}".encodeToByteArray())
        val output = ByteArrayOutputStream(); val zip = StreamingZip { output.write(it) }
        for ((name, bytes) in contents) {
            val crc = CRC32().apply { update(bytes) }.value
            assertEquals(crc, ExportCrc32().apply { update(bytes) }.value)
            zip.entry(name, bytes.size.toLong(), crc) { write ->
                var offset = 0
                while (offset < bytes.size) { val end = minOf(offset + 8192, bytes.size); write(bytes.copyOfRange(offset, end)); offset = end }
            }
        }
        zip.finish()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            for ((name, bytes) in contents) {
                val entry = assertNotNull(input.nextEntry); assertEquals(name, entry.name); assertEquals(0, entry.method)
                assertContentEquals(bytes, input.readBytes()); input.closeEntry()
            }
            assertNull(input.nextEntry)
        }
    }
    @Test fun entryCountUsesZip64AndIsReadableByJdkZipFile() = runTest {
        val file = Files.createTempFile("comic-zip64", ".zip").toFile()
        try {
            file.outputStream().buffered().use { output ->
                val zip = StreamingZip { output.write(it) }
                repeat(65535) { index -> zip.entry("$index.png", 0, 0) { } }
                zip.finish()
            }
            ZipFile(file).use { zip -> assertEquals(65535, zip.size()); assertNotNull(zip.getEntry("65534.png")) }
        } finally { file.delete() }
    }
    @Test fun checksumAndSizeChangesNeverProduceSuccess() = runTest {
        val output = ByteArrayOutputStream()
        assertFailsWith<java.io.IOException> {
            StreamingZip { output.write(it) }.entry("page.png", 3, 0) { it(byteArrayOf(1, 2, 3)) }
        }
        assertFailsWith<java.io.IOException> {
            StreamingZip { output.write(it) }.entry("page.png", 2, 0) { it(byteArrayOf(1, 2, 3)) }
        }
    }
}

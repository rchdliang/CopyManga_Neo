package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** PKWARE APPNOTE 6.3.10: STORE entries, UTF-8 names and ZIP64 sizes/offsets/counts.
 * CRC and sizes precede each entry, so even sequential ZIP readers can open the output.
 * Only central-directory metadata and one source chunk are retained in memory.
 */
internal class StreamingZip(private val write: suspend (ByteArray) -> Unit) {
    private data class Entry(val name: ByteArray, val size: Long, val crc: Long, val offset: Long)
    private val entries = mutableListOf<Entry>()
    private var position = 0L
    private var finished = false
    private suspend fun emit(bytes: ByteArray) { write(bytes); position += bytes.size }
    suspend fun entry(name: String, size: Long, crc: Long, copy: suspend (suspend (ByteArray) -> Unit) -> Unit) {
        check(!finished); require(size >= 0 && crc in 0..MAX32)
        require(!name.startsWith('/') && name.split('/').none { it == ".." || it.isEmpty() })
        val encoded = name.encodeToByteArray(); require(encoded.size <= 65535)
        val offset = position
        val large = size >= MAX32
        val extra = if (large) record { u16(1); u16(16); u64(size); u64(size) } else byteArrayOf()
        emit(record {
            u32(0x04034b50); u16(if (large) 45 else 20); u16(0x800); u16(0); u16(0); u16(33)
            u32(crc); u32(if (large) MAX32 else size); u32(if (large) MAX32 else size)
            u16(encoded.size); u16(extra.size); bytes(encoded); bytes(extra)
        })
        var copied = 0L; val actualCrc = ExportCrc32()
        copy { chunk ->
            currentCoroutineContext().ensureActive()
            if (copied > size - chunk.size) throw PlatformIOException("图片读取长度已变化")
            actualCrc.update(chunk); emit(chunk); copied += chunk.size
        }
        if (copied != size || actualCrc.value != crc) throw PlatformIOException("图片在导出时发生变化")
        entries += Entry(encoded, size, crc, offset)
    }
    suspend fun finish() {
        check(!finished); finished = true
        val start = position
        for (entry in entries) {
            val large = entry.size >= MAX32; val far = entry.offset >= MAX32
            val extra = if (large || far) record {
                u16(1); u16((if (large) 16 else 0) + if (far) 8 else 0)
                if (large) { u64(entry.size); u64(entry.size) }; if (far) u64(entry.offset)
            } else byteArrayOf()
            emit(record {
                u32(0x02014b50); u16(45); u16(if (large || far) 45 else 20); u16(0x800)
                u16(0); u16(0); u16(33); u32(entry.crc)
                u32(if (large) MAX32 else entry.size); u32(if (large) MAX32 else entry.size)
                u16(entry.name.size); u16(extra.size); u16(0); u16(0); u16(0); u32(0)
                u32(if (far) MAX32 else entry.offset); bytes(entry.name); bytes(extra)
            })
        }
        val length = position - start
        if (entries.size >= 65535 || start >= MAX32 || length >= MAX32) {
            val zip64 = position
            emit(record {
                u32(0x06064b50); u64(44); u16(45); u16(45); u32(0); u32(0)
                u64(entries.size.toLong()); u64(entries.size.toLong()); u64(length); u64(start)
                u32(0x07064b50); u32(0); u64(zip64); u32(1)
            })
        }
        emit(record {
            u32(0x06054b50); u16(0); u16(0); u16(entries.size.coerceAtMost(65535)); u16(entries.size.coerceAtMost(65535))
            u32(length.coerceAtMost(MAX32)); u32(start.coerceAtMost(MAX32)); u16(0)
        })
    }
    private class Record {
        private val values = ArrayList<Byte>()
        fun u16(value: Int) { repeat(2) { values += (value ushr (8 * it)).toByte() } }
        fun u32(value: Long) { repeat(4) { values += (value ushr (8 * it)).toByte() } }
        fun u64(value: Long) { repeat(8) { values += (value ushr (8 * it)).toByte() } }
        fun bytes(value: ByteArray) { values.addAll(value.toList()) }
        fun result() = values.toByteArray()
    }
    private fun record(block: Record.() -> Unit) = Record().apply(block).result()
    companion object { private const val MAX32 = 0xffff_ffffL }
}

internal class ExportCrc32 {
    private var crc = -1
    val value: Long get() = crc.inv().toLong() and 0xffff_ffffL
    fun update(bytes: ByteArray) { for (byte in bytes) crc = table[(crc xor byte.toInt()) and 255] xor (crc ushr 8) }
    companion object {
        private val table = IntArray(256) { index ->
            var value = index; repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor 0xedb88320.toInt() else value ushr 1 }; value
        }
    }
}

internal suspend fun writeComicArchive(
    unit: ExportUnit, format: ComicExportFormat, write: suspend (ByteArray) -> Unit,
    readPage: suspend (ComicExportPage, suspend (ByteArray) -> Unit) -> Unit,
    onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
) {
    require(format != ComicExportFormat.IMAGES)
    val zip = StreamingZip(write)
    for (chapter in unit.chapters) {
        onChapter("${unit.comic.name}／${chapter.name}")
        for ((index, page) in chapter.pages.withIndex()) {
            val crc = ExportCrc32(); readPage(page) { crc.update(it) }
            val prefix = if (format == ComicExportFormat.CBZ) "" else "${exportGroupName(unit.comic, chapter.group)}/${chapterExportName(chapter)}/"
            zip.entry(prefix + exportPageName(index, page), page.size, crc.value) { emit ->
                readPage(page) { chunk -> emit(chunk); onBytes(chunk.size.toLong()) }
            }
        }
    }
    val manifest = exportManifest(unit).encodeToByteArray(); val crc = ExportCrc32().apply { update(manifest) }
    zip.entry("manifest.json", manifest.size.toLong(), crc.value) { it(manifest) }
    zip.finish()
}

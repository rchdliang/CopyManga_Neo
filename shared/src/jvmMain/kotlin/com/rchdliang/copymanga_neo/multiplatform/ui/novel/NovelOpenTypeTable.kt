package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Table offsets in TTC collections are relative to the entire file, not the selected face. */
internal fun novelOpenTypeTable(buffer: ByteBuffer, ttcIndex: Int, tag: Int): ByteBuffer? = runCatching {
    val data = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
    val sfnt = if (data.getInt(0) == 0x74746366) {
        require(ttcIndex >= 0 && ttcIndex < data.getInt(8))
        data.getInt(Math.addExact(12, Math.multiplyExact(ttcIndex, 4)))
    } else {
        require(ttcIndex == 0)
        0
    }
    require(sfnt >= 0 && sfnt.toLong() + 12 <= data.limit())
    require(data.getInt(sfnt) in setOf(0x00010000, 0x4f54544f))
    val count = data.getShort(sfnt + 4).toInt() and 0xffff
    require(sfnt.toLong() + 12 + count * 16L <= data.limit())
    for (index in 0 until count) {
        val record = sfnt + 12 + index * 16
        if (data.getInt(record) != tag) continue
        val start = data.getInt(record + 8)
        val length = data.getInt(record + 12)
        require(start >= 0 && length >= 0 && start.toLong() + length <= data.limit())
        data.position(start)
        data.limit(start + length)
        return@runCatching data.slice().order(ByteOrder.BIG_ENDIAN)
    }
    null
}.getOrNull()

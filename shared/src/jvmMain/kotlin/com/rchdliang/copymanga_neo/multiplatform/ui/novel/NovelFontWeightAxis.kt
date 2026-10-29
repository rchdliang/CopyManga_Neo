package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.nio.ByteBuffer

/** Read the supported wght range; Android Font.axes only exposes currently applied coordinates. */
internal fun novelFontWeightAxis(buffer: ByteBuffer, ttcIndex: Int): ClosedFloatingPointRange<Float>? = runCatching {
    val data = novelOpenTypeTable(buffer, ttcIndex, 0x66766172) ?: return null // fvar
    fun u16(offset: Int) = data.getShort(offset).toInt() and 0xffff
    val axesOffset = u16(4)
    val axesCount = u16(8)
    val axisSize = u16(10)
    require(axesOffset >= 16 && axisSize >= 20 && axesOffset.toLong() + axesCount * axisSize.toLong() <= data.limit())
    for (axis in 0 until axesCount) {
        val at = axesOffset + axis * axisSize
        if (data.getInt(at) == 0x77676874) { // wght, 16.16 fixed point
            val min = data.getInt(at + 4) / 65536f
            val max = data.getInt(at + 12) / 65536f
            require(min <= max)
            return@runCatching min..max
        }
    }
    null
}.getOrNull()

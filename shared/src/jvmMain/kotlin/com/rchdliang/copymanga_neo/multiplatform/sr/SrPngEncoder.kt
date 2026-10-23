package com.rchdliang.copymanga_neo.multiplatform.sr

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** RGBA8/sRGB PNG, row streamed: no second output-sized bitmap or unbounded encoded buffer. */
internal fun encodeSrPng(raster: SrRaster, limit: Int = 16*1024*1024): ByteArray {
    require(raster.width in 1..8192 && raster.height in 1..8192 && limit > 0)
    require(raster.rgba.size.toLong() == raster.width.toLong()*raster.height*4)
    val bytes = object : ByteArrayOutputStream(minOf(32768, limit)) {
        override fun write(b: Int) { require(count < limit) { "超分结果超过缓存限制" }; super.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(count.toLong()+len <= limit) { "超分结果超过缓存限制" }
            super.write(b, off, len)
        }
    }
    val output = DataOutputStream(bytes)
    fun chunk(type: String, data: ByteArray, size: Int = data.size) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(name); update(data, 0, size) }
        output.writeInt(size); output.write(name); output.write(data, 0, size); output.writeInt(crc.value.toInt())
    }
    output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    val header = ByteArrayOutputStream(13)
    DataOutputStream(header).apply { writeInt(raster.width); writeInt(raster.height); write(byteArrayOf(8, 6, 0, 0, 0)) }
    chunk("IHDR", header.toByteArray()); chunk("sRGB", byteArrayOf(0))
    val deflater = Deflater(6)
    val buffer = ByteArray(32768)
    fun drain() {
        while (!deflater.needsInput()) {
            val count = deflater.deflate(buffer)
            if (count > 0) chunk("IDAT", buffer, count)
        }
    }
    try {
        val filter = byteArrayOf(0)
        for (row in 0 until raster.height) {
            deflater.setInput(filter); drain()
            deflater.setInput(raster.rgba, row*raster.width*4, raster.width*4); drain()
        }
        deflater.finish()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) chunk("IDAT", buffer, count)
        }
    } finally { deflater.end() }
    chunk("IEND", byteArrayOf())
    return bytes.toByteArray()
}

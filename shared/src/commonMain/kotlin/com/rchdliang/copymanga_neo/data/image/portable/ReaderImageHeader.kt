package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.data.export.portable.imageExtension

/** Bounded encoded-metadata inspection for targets without a metadata-only native decoder.
 * No raster or thumbnail is allocated. Like Android inJustDecodeBounds this does not decode pixels. */
internal fun readerImageHeader(bytes: ByteArray): ReaderImageMetadata {
    fun need(offset: Int, count: Int) { require(offset >= 0 && count >= 0 && offset.toLong() + count <= bytes.size) { "图片内容不完整" } }
    fun u8(p: Int): Int { need(p, 1); return bytes[p].toInt() and 255 }
    fun be16(p: Int) = (u8(p) shl 8) or u8(p + 1)
    fun le16(p: Int) = u8(p) or (u8(p + 1) shl 8)
    fun be32(p: Int): Long = (u8(p).toLong() shl 24) or (u8(p + 1).toLong() shl 16) or (u8(p + 2).toLong() shl 8) or u8(p + 3).toLong()
    fun le32(p: Int): Long = u8(p).toLong() or (u8(p + 1).toLong() shl 8) or (u8(p + 2).toLong() shl 16) or (u8(p + 3).toLong() shl 24)
    fun ascii(p: Int, count: Int): String { need(p, count); return buildString(count) { repeat(count) { append(u8(p + it).toChar()) } } }
    fun metadata(mime: String, width: Long, height: Long): ReaderImageMetadata {
        require(width in 1..Int.MAX_VALUE.toLong() && height in 1..Int.MAX_VALUE.toLong()) { "图片尺寸无效" }
        return ReaderImageMetadata(mime, width.toInt(), height.toInt())
    }
    return when (imageExtension(bytes.copyOf(minOf(bytes.size, 64)))) {
        "png" -> {
            need(0, 33)
            require(be32(8) == 13L && ascii(12, 4) == "IHDR") { "PNG 图片头无效" }
            val depths = when (u8(25)) { 0 -> listOf(1, 2, 4, 8, 16); 2, 4, 6 -> listOf(8, 16); 3 -> listOf(1, 2, 4, 8); else -> emptyList() }
            require(u8(24) in depths && u8(26) == 0 && u8(27) == 0 && u8(28) in 0..1) { "PNG 编码参数无效" }
            metadata("image/png", be32(16), be32(20))
        }
        "gif" -> {
            need(0, 13)
            if (u8(10) and 128 != 0) need(13, 3 * (1 shl ((u8(10) and 7) + 1)))
            metadata("image/gif", le16(6).toLong(), le16(8).toLong())
        }
        "jpg" -> {
            var p = 2
            var markers = 0
            var result: ReaderImageMetadata? = null
            while (p < bytes.size && result == null) {
                require(++markers <= 4096) { "JPEG 标记过多" }
                require(u8(p++) == 255) { "JPEG 标记无效" }
                while (p < bytes.size && u8(p) == 255) p++
                val marker = u8(p++)
                require(marker != 0 && marker != 217 && marker != 218) { "JPEG 缺少尺寸信息" }
                if (marker == 216 || marker == 1 || marker in 208..215) continue
                val size = be16(p)
                require(size >= 2) { "JPEG 标记长度无效" }
                need(p, size)
                if (marker in listOf(192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207)) {
                    require(size >= 8 && u8(p + 7) > 0 && size == 8 + 3 * u8(p + 7)) { "JPEG 帧头无效" }
                    result = metadata("image/jpeg", be16(p + 5).toLong(), be16(p + 3).toLong())
                }
                p += size
            }
            requireNotNull(result) { "JPEG 缺少尺寸信息" }
        }
        "webp" -> {
            need(0, 20)
            val end = le32(4) + 8
            require(end in 20..bytes.size.toLong()) { "WebP 图片不完整" }
            var p = 12
            var result: ReaderImageMetadata? = null
            while (p.toLong() + 8 <= end && result == null) {
                val kind = ascii(p, 4)
                val size = le32(p + 4)
                val data = p + 8
                require(size <= end - data) { "WebP 区块无效" }
                result = when (kind) {
                    "VP8X" -> {
                        require(size >= 10)
                        val width = u8(data + 4) or (u8(data + 5) shl 8) or (u8(data + 6) shl 16)
                        val height = u8(data + 7) or (u8(data + 8) shl 8) or (u8(data + 9) shl 16)
                        metadata("image/webp", width.toLong() + 1, height.toLong() + 1)
                    }
                    "VP8 " -> {
                        require(size >= 10 && u8(data + 3) == 157 && u8(data + 4) == 1 && u8(data + 5) == 42) { "WebP 帧头无效" }
                        metadata("image/webp", (le16(data + 6) and 16383).toLong(), (le16(data + 8) and 16383).toLong())
                    }
                    "VP8L" -> {
                        require(size >= 5 && u8(data) == 47) { "WebP 无损帧头无效" }
                        val bits = le32(data + 1)
                        metadata("image/webp", (bits and 16383) + 1, ((bits shr 14) and 16383) + 1)
                    }
                    else -> null
                }
                p = (data + size + (size and 1)).toInt()
            }
            requireNotNull(result) { "WebP 缺少尺寸信息" }
        }
        "avif", "heic" -> {
            var visited = 0
            var largest: ReaderImageMetadata? = null
            val mime = if (imageExtension(bytes.copyOf(minOf(bytes.size, 64))) == "avif") "image/avif" else "image/heif"
            fun boxes(start: Int, end: Int, depth: Int) {
                require(depth <= 8) { "图片容器嵌套过深" }
                var p = start
                while (p < end) {
                    require(++visited <= 4096 && end - p >= 8) { "图片容器区块无效" }
                    val kind = ascii(p + 4, 4)
                    var header = 8
                    var size = be32(p)
                    if (size == 1L) {
                        require(end - p >= 16)
                        val high = be32(p + 8)
                        val low = be32(p + 12)
                        require(high == 0L) { "图片区块超过安全大小" }
                        size = low; header = 16
                    } else if (size == 0L) size = (end - p).toLong()
                    require(size >= header && size <= end - p) { "图片容器区块不完整" }
                    val next = p + size.toInt()
                    val data = p + header
                    when (kind) {
                        "meta" -> { require(next - data >= 4); boxes(data + 4, next, depth + 1) }
                        "iprp", "ipco" -> boxes(data, next, depth + 1)
                        "ispe" -> {
                            require(next - data >= 12 && u8(data) == 0) { "图片空间属性无效" }
                            val candidate = metadata(mime, be32(data + 4), be32(data + 8))
                            if (largest == null || candidate.width.toLong() * candidate.height > largest!!.width.toLong() * largest!!.height) largest = candidate
                        }
                    }
                    p = next
                }
            }
            boxes(0, bytes.size, 0)
            requireNotNull(largest) { "图片容器缺少尺寸信息" }
        }
        else -> error("不支持导出此图片格式")
    }
}

package com.rchdliang.copymanga_neo.multiplatform.sr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

internal class AndroidSrCodec : SrRasterCodec {
    override fun dimensions(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType in setOf("image/png", "image/jpeg", "image/webp")) { "此格式暂不增强" }
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        require(orientation in setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL)) { "此图片包含旋转信息，继续显示原图" }
        // WebP animation is represented by the ANIM chunk; reject instead of flattening it.
        if (options.outMimeType == "image/webp") {
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val name = String(bytes, offset, 4, Charsets.US_ASCII)
                require(name != "ANIM") { "动画图片暂不增强" }
                val size = (0..3).fold(0L) { v, i -> v or ((bytes[offset+4+i].toLong() and 255) shl (i*8)) }
                if (size > bytes.size-offset-8) break
                offset += 8 + size.toInt() + (size.toInt() and 1)
            }
        }
        return options.outWidth to options.outHeight
    }
    override fun decode(bytes: ByteArray): SrRaster {
        val (width, height) = dimensions(bytes)
        require(width in 1..4096 && height in 1..4096 && width.toLong()*height <= 3L*1024*1024)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false
            inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
        }))
        try {
            require(bitmap.width == width && bitmap.height == height)
            val row = IntArray(width); val pixels = ByteArray(width*height*4)
            for (y in 0 until height) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    val c = row[x]; val i = (y*width+x)*4
                    pixels[i] = (c ushr 16).toByte(); pixels[i+1] = (c ushr 8).toByte()
                    pixels[i+2] = c.toByte(); pixels[i+3] = (c ushr 24).toByte()
                }
            }
            return SrRaster(width, height, pixels)
        } finally { bitmap.recycle() }
    }
    override fun encode(raster: SrRaster): ByteArray = encodeSrPng(raster)
}

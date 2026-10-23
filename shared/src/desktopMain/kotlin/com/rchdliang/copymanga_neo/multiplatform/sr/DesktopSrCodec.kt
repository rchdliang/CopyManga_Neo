package com.rchdliang.copymanga_neo.multiplatform.sr

import org.jetbrains.skia.*

internal class DesktopSrCodec : SrRasterCodec {
    override fun dimensions(bytes: ByteArray): Pair<Int, Int> = Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use { codec ->
            require(codec.encodedOrigin == EncodedOrigin.TOP_LEFT && codec.frameCount <= 1) {
                "此图片包含旋转或动画信息，继续显示原图"
            }
            codec.width to codec.height
        }
    }
    override fun decode(bytes: ByteArray): SrRaster {
        val (width, height) = dimensions(bytes)
        require(width in 1..4096 && height in 1..4096 && width.toLong()*height <= 3L*1024*1024)
        return Image.makeFromEncoded(bytes).use { image ->
            val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB)
            val pixels = Bitmap().use { bitmap ->
                check(bitmap.allocPixels(info)); check(image.readPixels(bitmap))
                requireNotNull(bitmap.readPixels(info, width*4)) { "原图解码失败" }
            }
            SrRaster(width, height, pixels)
        }
    }
    override fun encode(raster: SrRaster): ByteArray = encodeSrPng(raster)
}

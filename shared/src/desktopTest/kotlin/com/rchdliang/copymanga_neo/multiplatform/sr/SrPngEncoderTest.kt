package com.rchdliang.copymanga_neo.multiplatform.sr

import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.*

class SrPngEncoderTest {
    @Test fun roundTripMultipleRowsChunksColorsAndTransparency() {
        val pixels = Random(10).nextBytes(257*193*4)
        val png = encodeSrPng(SrRaster(257, 193, pixels))
        val image = ImageIO.read(png.inputStream())
        assertEquals(257, image.width); assertEquals(193, image.height)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val i = (y*image.width+x)*4
            val argb = ((pixels[i+3].toInt() and 255) shl 24) or ((pixels[i].toInt() and 255) shl 16) or
                ((pixels[i+1].toInt() and 255) shl 8) or (pixels[i+2].toInt() and 255)
            assertEquals(argb, image.getRGB(x, y))
        }
    }
    @Test fun incompressibleOutputStopsAtEncodedBudget() {
        assertFailsWith<IllegalArgumentException> {
            encodeSrPng(SrRaster(256, 256, Random(7).nextBytes(256*256*4)), limit = 4096)
        }
    }
}

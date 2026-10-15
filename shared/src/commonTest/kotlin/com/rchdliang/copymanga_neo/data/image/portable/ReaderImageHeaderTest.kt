package com.rchdliang.copymanga_neo.data.image.portable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ReaderImageHeaderTest {
    private fun bytes(vararg value: Int) = ByteArray(value.size) { value[it].toByte() }
    private fun be(value: Int) = bytes(value ushr 24, value ushr 16, value ushr 8, value)
    private fun le(value: Int) = bytes(value, value ushr 8, value ushr 16, value ushr 24)
    private fun box(type: String, payload: ByteArray) = be(payload.size + 8) + type.encodeToByteArray() + payload
    private fun png() = bytes(137,80,78,71,13,10,26,10) + be(13) + "IHDR".encodeToByteArray() + be(2) + be(3) + bytes(8,6,0,0,0) + be(0)

    @Test fun pngAndGifDimensionsDoNotAllocatePixels() {
        assertEquals(ReaderImageMetadata("image/png", 2, 3), readerImageHeader(png()))
        val gif = "GIF89a".encodeToByteArray() + bytes(2,0,3,0,0,0,0)
        assertEquals(ReaderImageMetadata("image/gif", 2, 3), readerImageHeader(gif))
    }
    @Test fun jpegSkipsMetadataSegmentsAndReadsSof() {
        val jpeg = bytes(255,216,255,225,0,4,10,20,255,192,0,11,8,0,3,0,2,1,1,17,0,255,217)
        assertEquals(ReaderImageMetadata("image/jpeg", 2, 3), readerImageHeader(jpeg))
    }
    @Test fun webpExtendedAndLosslessReadDimensions() {
        val extended = "VP8X".encodeToByteArray() + le(10) + bytes(0,0,0,0,1,0,0,2,0,0)
        val webp = "RIFF".encodeToByteArray() + le(extended.size + 4) + "WEBP".encodeToByteArray() + extended
        assertEquals(ReaderImageMetadata("image/webp", 2, 3), readerImageHeader(webp))
        val lossless = "VP8L".encodeToByteArray() + le(5) + bytes(47) + le(1 or (2 shl 14)) + bytes(0)
        assertEquals(ReaderImageMetadata("image/webp", 2, 3), readerImageHeader("RIFF".encodeToByteArray() + le(lossless.size + 4) + "WEBP".encodeToByteArray() + lossless))
    }
    @Test fun avifAndHeifContainerDimensionsAreReadOnlyFromStructuralBoxes() {
        val properties = box("meta", be(0) + box("iprp", box("ipco", box("ispe", be(0) + be(2) + be(3)))))
        for ((brand, mime) in listOf("avif" to "image/avif", "heic" to "image/heif")) {
            val image = box("ftyp", brand.encodeToByteArray() + be(0) + brand.encodeToByteArray()) + properties
            assertEquals(ReaderImageMetadata(mime, 2, 3), readerImageHeader(image))
        }
    }
    @Test fun malformedOrTruncatedHeadersFailWithoutOutOfRangeReads() {
        val image = png()
        for (size in image.indices) assertFails { readerImageHeader(image.copyOf(size)) }
        assertFails { readerImageHeader("<html>challenge</html>".encodeToByteArray()) }
        assertFails { readerImageHeader(bytes(255,216,255,225,255,255)) }
    }
    @Test fun nonpositiveAndOverflowingDimensionsAreRejected() {
        val zero = png().also { be(0).copyInto(it, 16) }
        val overflow = png().also { bytes(255,255,255,255).copyInto(it, 16) }
        assertFails { readerImageHeader(zero) }
        assertFails { readerImageHeader(overflow) }
    }
    @Test fun containerCannotClaimUnboundedLengthOrHidePropertiesInsidePayload() {
        val ftyp = box("ftyp", "avif".encodeToByteArray() + be(0) + "avif".encodeToByteArray())
        assertFails { readerImageHeader(ftyp + be(1) + "meta".encodeToByteArray() + be(Int.MAX_VALUE) + be(0)) }
        assertFails { readerImageHeader(ftyp + box("mdat", box("ispe", be(0) + be(2) + be(3)))) }
    }
}

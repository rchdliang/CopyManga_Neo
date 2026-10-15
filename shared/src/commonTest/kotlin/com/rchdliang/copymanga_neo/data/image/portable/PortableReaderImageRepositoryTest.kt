package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortableReaderImageRepositoryTest {
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)
    private class Io : ReaderImageIo {
        var cached: ByteArray? = null
        var local = byteArrayOf()
        var metadata = ReaderImageMetadata("image/png", 1, 100000)
        var inspectionCount = 0
        var cutoff: Long? = null
        var published: ByteArray? = null
        var saved: ByteArray? = null
        var failure: Exception? = null
        override suspend fun prunePrepared(beforeMillis: Long) { cutoff = beforeMillis }
        override suspend fun readCached(source: String, maximumBytes: Int) = cached
        override suspend fun readLocal(source: String, maximumBytes: Int) = local
        override suspend fun inspect(bytes: ByteArray): ReaderImageMetadata { inspectionCount++; return metadata }
        override suspend fun publish(displayName: String, bytes: ByteArray): String { published = bytes; return "/private/$displayName" }
        override suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int) = requireNotNull(published)
        override suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray) { failure?.let { throw it }; saved = bytes }
        override suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray) { failure?.let { throw it }; saved = readValidatedBytes() }
    }
    private fun repository(io: Io, read: suspend (String, Int) -> ByteArray = { _, _ -> png }) =
        PortableReaderImageRepository(ReaderImageTransport(read), io, nowMillis = { 500_000_000 }, newId = { "12345678-1234" })

    @Test fun cachedOriginalBytesAvoidNetworkAndKeepShareLease() = runTest {
        val io = Io().apply { cached = png }
        val result = repository(io) { _, _ -> error("cache hit must not fetch") }.prepare("https://cdn/image", " 标题/a\u0001 ").getOrThrow()
        assertEquals("标题_a__12345678.png", result.displayName)
        assertEquals("image/png", result.mimeType)
        assertContentEquals(png, io.published)
        assertEquals(500_000_000L - PortableReaderImageRepository.SHARE_RETENTION_MILLIS, io.cutoff)
    }
    @Test fun localDownloadedImagesNeverTouchNetwork() = runTest {
        val io = Io().apply { local = png }
        assertTrue(repository(io) { _, _ -> error("local must not fetch") }.prepare("file:///download/1.png", "").isSuccess)
        assertContentEquals(png, io.published)
    }
    @Test fun networkFallbackReceivesSameUrlAndHardBound() = runTest {
        val io = Io()
        val source = "http://cdn/image"
        val image = repository(io) { url, limit ->
            assertEquals(source, url)
            assertEquals(64 * 1024 * 1024, limit)
            png
        }.prepare(source, "").getOrThrow()
        assertEquals("漫画图片_12345678.png", image.displayName)
    }
    @Test fun oversizedPayloadIsRejectedBeforeNativeProbeOrPublish() = runTest {
        val io = Io().apply { cached = png }
        val repo = PortableReaderImageRepository(ReaderImageTransport { _, _ -> png }, io, maximumImageBytes = 8)
        assertTrue(repo.prepare("https://cdn/image", "title").isFailure)
        assertEquals(0, io.inspectionCount)
        assertEquals(null, io.published)
    }
    @Test fun invalidCacheDoesNotSilentlyDownloadReplacement() = runTest {
        val io = Io().apply { cached = "<html>captcha</html>".encodeToByteArray() }
        assertTrue(repository(io) { _, _ -> error("corrupt cache must not fetch") }.prepare("https://cdn/image", "title").isFailure)
        assertEquals(0, io.inspectionCount)
    }
    @Test fun mismatchAndInvalidDimensionsDoNotPublish() = runTest {
        for (metadata in listOf(ReaderImageMetadata("image/jpeg", 1, 1), ReaderImageMetadata("image/png", 0, 1))) {
            val io = Io().apply { cached = png; this.metadata = metadata }
            assertTrue(repository(io).prepare("https://cdn/image", "title").isFailure)
            assertEquals(null, io.published)
        }
    }
    @Test fun cancellationEscapesResultRatherThanBecomingErrorMessage() = runTest {
        assertFailsWith<CancellationException> {
            repository(Io()) { _, _ -> throw CancellationException("cancel") }.prepare("https://cdn/image", "title")
        }
    }
    @Test fun saveRevalidatesOriginalAndPreservesBytes() = runTest {
        val io = Io().apply { cached = png }
        val repo = repository(io)
        val image = repo.prepare("https://cdn/image", "title").getOrThrow()
        repo.saveToDocument(image, "/chosen/image.png").getOrThrow()
        assertContentEquals(png, io.saved)
        io.saved = null
        assertTrue(repo.saveToGallery(image.copy(mimeType = "image/jpeg")).isFailure)
        assertEquals(null, io.saved)
    }
    @Test fun failedNativeWriteNeverReportsSuccess() = runTest {
        val io = Io().apply { cached = png; failure = IllegalStateException("disk full") }
        val repo = repository(io)
        val image = repo.prepare("https://cdn/image", "title").getOrThrow()
        assertTrue(repo.saveToGallery(image).isFailure)
        assertTrue(repo.saveToDocument(image, "/chosen/image.png").isFailure)
        assertEquals(null, io.saved)
    }
    @Test fun safeNamesRetainOriginalEightyCharacterPolicy() {
        assertEquals(80, readerImageSafeTitle("甲".repeat(100)).length)
        assertEquals("漫画图片", readerImageSafeTitle("   "))
        assertEquals("_", readerImageSafeTitle(" \t "))
        assertFalse(readerImageSafeTitle("a/b\\c:*?\"<>|\u0000\u007f").any { it in "/\\:*?\"<>|" || it.code < 32 || it.code == 127 })
        assertEquals("heic", readerImageExtension("image/heif"))
        assertEquals("avif", readerImageExtension("image/avif"))
    }
}

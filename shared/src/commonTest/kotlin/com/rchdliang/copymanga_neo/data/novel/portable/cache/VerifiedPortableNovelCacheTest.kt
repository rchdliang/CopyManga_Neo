package com.rchdliang.copymanga_neo.data.novel.portable.cache

import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class VerifiedPortableNovelCacheTest {
    private class Files : CacheStorage {
        val data = mutableMapOf<String, ByteArray>()
        var rejectManifest = false
        override suspend fun read(path: String, limit: Int): ByteArray? = data[path]?.copyOf()?.also {
            if (it.size > limit) throw PlatformIOException("too large")
        }
        override suspend fun write(path: String, bytes: ByteArray) {
            if (rejectManifest && path.endsWith("/manifest.json")) throw PlatformIOException("disk full")
            data[path] = bytes.copyOf()
        }
        override suspend fun list(prefix: String) = data.keys.filter { prefix.isEmpty() || it == prefix || it.startsWith("$prefix/") }
        override suspend fun remove(path: String) { data.keys.filter { it == path || it.startsWith("$path/") }.toList().forEach(data::remove) }
        override suspend fun size(path: String) = data.getValue(path).size.toLong()
        override suspend fun availableBytes() = 1_000_000L
        override suspend fun imageLocation(path: String) = "memory://$path"
    }
    private val mode = RequestMode.WEB
    private fun metadata(image: Boolean = false) = """{"book":{"path_word":"book","name":"标题","author":[]},"volume":{"id":"v1","name":"第一卷","txt_encoding":"UTF-8","contents":[{"content_type":1,"name":"章","start_lines":0,"end_lines":1}${if (image) ",{\"content_type\":2,\"name\":\"插图\",\"start_lines\":0,\"end_lines\":1,\"content\":\"https://example.org/image\"}" else ""}]}}"""

    @Test
    fun failedManifestPublishKeepsPreviousReadableRevision() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        cache.publish(mode, "owner", "book", "v1", metadata(), "旧正文".encodeToByteArray())
        files.rejectManifest = true
        assertFailsWith<PlatformIOException> {
            cache.publish(mode, "owner", "book", "v1", metadata(), "新正文".encodeToByteArray())
        }
        files.rejectManifest = false
        assertEquals(listOf("旧正文"), cache.read(mode, "owner", "book", "v1")!!.document.lines)
    }
    @Test
    fun malformedTextDoesNotPublishAndAccountCachesRemainIsolated() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        assertFailsWith<CharacterCodingException> {
            cache.publish(mode, "owner", "book", "v1", metadata(), byteArrayOf(0xc3.toByte(), 0x28))
        }
        assertTrue(files.data.isEmpty())
        cache.publish(mode, "owner", "book", "v1", metadata(), "正文".encodeToByteArray())
        assertNull(cache.read(mode, "someone-else", "book", "v1"))
        assertNull(cache.read(RequestMode.OFFICIAL_APP, "owner", "book", "v1"))
        assertEquals(1, cache.list().size)
    }
    @Test
    fun verifiedOfflineImagesBecomeMissingWhenTamperedWith() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        assertFalse(cache.publish(mode, "owner", "book", "v1", metadata(true), "正文".encodeToByteArray()).offlineReady)
        val cached = cache.writeImage(mode, "owner", "book", "v1", "https://example.org/image", byteArrayOf(1, 2, 3))
        assertTrue(cached.offlineReady)
        assertTrue(cached.cachedImages.values.single().startsWith("memory://"))
        val image = files.data.keys.single { it.endsWith(".img") }
        files.data[image] = byteArrayOf(9)
        val repaired = cache.read(mode, "owner", "book", "v1")!!
        assertFalse(repaired.offlineReady)
        assertTrue(repaired.cachedImages.isEmpty())
        assertEquals(listOf("正文"), repaired.document.lines)
    }
    @Test
    fun damagedTextIsNotSilentlyTrustedOrDeleted() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        cache.publish(mode, "owner", "book", "v1", metadata(), "正文".encodeToByteArray())
        files.data[files.data.keys.single { it.endsWith(".txt") }] = "tampered".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { cache.read(mode, "owner", "book", "v1") }
        assertTrue(cache.list().isEmpty())
        assertTrue(files.data.keys.any { it.endsWith("/manifest.json") })
        cache.removeBook(mode, "owner", "book")
        assertTrue(files.data.isEmpty())
    }

    @Test
    fun corruptManifestCanBeReplacedOnlyAfterNewContentValidates() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        cache.publish(mode, "owner", "book", "v1", metadata(), "old".encodeToByteArray())
        val manifest = files.data.keys.single { it.endsWith("/manifest.json") }
        files.data[manifest] = "broken json".encodeToByteArray()
        val before = files.data.mapValues { it.value.toList() }
        assertFailsWith<CharacterCodingException> {
            cache.publish(mode, "owner", "book", "v1", metadata(), byteArrayOf(0xc3.toByte(), 0x28))
        }
        assertEquals(before, files.data.mapValues { it.value.toList() })
        files.rejectManifest = true
        assertFailsWith<PlatformIOException> {
            cache.publish(mode, "owner", "book", "v1", metadata(), "new".encodeToByteArray())
        }
        assertEquals("broken json", files.data.getValue(manifest).decodeToString())
        assertTrue(before.keys.all { it in files.data })
        files.rejectManifest = false
        cache.publish(mode, "owner", "book", "v1", metadata(), "new".encodeToByteArray())
        assertEquals(listOf("new"), cache.read(mode, "owner", "book", "v1")!!.document.lines)
        assertEquals(1, files.data.keys.count { it.endsWith(".txt") })
    }

    @Test
    fun metadataExpiryAndClearGenerationPreventStaleRepopulation() = runTest {
        val files = Files()
        var now = 100L
        val cache = VerifiedPortableNovelCache(files) { now }
        val key = "a".repeat(64)
        val generation = cache.generation
        cache.writeMetadata(key, "{\"items\":[1]}", generation)
        assertEquals("{\"items\":[1]}", cache.readMetadata(key))
        now += 31L * 24 * 60 * 60 * 1000
        assertNull(cache.readMetadata(key))
        cache.clear()
        cache.writeMetadata(key, "{\"items\":[2]}", generation)
        assertNull(cache.readMetadata(key))
        assertTrue(files.data.isEmpty())
    }
}

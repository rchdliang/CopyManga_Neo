package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.data.image.portable.ReaderImageMetadata
import com.rchdliang.copymanga_neo.data.novel.portable.cache.VerifiedPortableNovelCache
import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlin.test.*

class PortableNovelRecoveryTest {
    private class Files : CacheStorage {
        val data = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String, limit: Int) = data[path]?.also { require(it.size <= limit) }
        override suspend fun write(path: String, bytes: ByteArray) { data[path] = bytes.copyOf() }
        override suspend fun list(prefix: String) = data.keys.filter { it == prefix || it.startsWith("$prefix/") }
        override suspend fun remove(path: String) { list(path).forEach(data::remove) }
        override suspend fun size(path: String) = data.getValue(path).size.toLong()
        override suspend fun availableBytes() = Long.MAX_VALUE
        override suspend fun imageLocation(path: String) = "memory://$path"
    }
    private val mode = RequestMode.WEB
    private fun metadata(image: Boolean = false) = """{"book":{"path_word":"book","name":"Title","author":[]},"volume":{"id":"v1","name":"Volume","txt_encoding":"UTF-8","txt_addr":"https://assets.example.org/text","contents":[{"content_type":1,"name":"Chapter","start_lines":0,"end_lines":1}${if (image) ",{\"content_type\":2,\"name\":\"Image\",\"start_lines\":0,\"end_lines\":1,\"content\":\"https://assets.example.org/image\"}" else ""}]}}"""

    @Test fun corruptTextAndManifestRecoverWithAndWithoutExplicitRefresh() = runTest {
        for (refresh in listOf(false, true)) for (corruptManifest in listOf(false, true)) {
            val files = Files(); val cache = VerifiedPortableNovelCache(files)
            cache.publish(mode, "guest", "book", "v1", metadata(), "old".encodeToByteArray())
            val damaged = files.data.keys.single { if (corruptManifest) it.endsWith("manifest.json") else it.endsWith(".txt") }
            files.data[damaged] = "corrupt".encodeToByteArray()
            var requests = 0
            val engine = HttpClient(MockEngine { request ->
                requests++
                if (request.url.host == "assets.example.org") respond("repaired", headers = headersOf(HttpHeaders.ContentType, "text/plain"))
                else respond("""{"code":200,"results":${metadata()}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
            val client = CopyMangaClient(engine, PortableNetworkState())
            try {
                val repository = PortableNovelRepository(client, cache)
                val reading = repository.readVolume(mode, "guest", "book", "v1", false, refresh, {}).getOrThrow()
                assertEquals(listOf("repaired"), reading.document.lines)
                assertEquals(2, requests)
                assertEquals(listOf("repaired"), cache.read(mode, "guest", "book", "v1")!!.document.lines)
            } finally { client.close(); engine.close() }
        }
    }

    @Test fun cancelledCacheReadDoesNotStartRepairRequest() = runTest {
        val underlying = VerifiedPortableNovelCache(Files())
        val cache = object : PortableNovelCache by underlying {
            override suspend fun read(mode: RequestMode, owner: String, path: String, id: String): com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume? {
                throw CancellationException("cancelled read")
            }
        }
        var requests = 0
        val engine = HttpClient(MockEngine { requests++; error("must not request") })
        val client = CopyMangaClient(engine, PortableNetworkState())
        try {
            assertFailsWith<CancellationException> {
                PortableNovelRepository(client, cache).readVolume(mode, "guest", "book", "v1", false, false, {})
            }
            assertEquals(0, requests)
        } finally { client.close(); engine.close() }
    }

    @Test fun rejectedIllustrationRemainsMissingAndCanBeRetried() = runTest {
        val files = Files(); val cache = VerifiedPortableNovelCache(files)
        cache.publish(mode, "guest", "book", "v1", metadata(true), "text".encodeToByteArray())
        var valid = false
        var inspections = 0
        val engine = HttpClient(MockEngine {
            respond(if (valid) "valid-image-fixture" else "<html>error</html>")
        })
        val client = CopyMangaClient(engine, PortableNetworkState())
        try {
            val repository = PortableNovelRepository(client, cache, inspectImage = { bytes ->
                inspections++
                if (bytes.decodeToString().startsWith("<html>")) ReaderImageMetadata("text/html", 0, 0)
                else ReaderImageMetadata("image/png", 1, 1)
            })
            assertTrue(repository.readVolume(mode, "guest", "book", "v1", true, false, {}).isFailure)
            assertFalse(cache.read(mode, "guest", "book", "v1")!!.offlineReady)
            assertTrue(files.data.keys.none { it.endsWith(".img") })
            valid = true
            assertTrue(repository.readVolume(mode, "guest", "book", "v1", true, false, {}).getOrThrow().offlineReady)
            assertEquals(2, inspections)
        } finally { client.close(); engine.close() }
    }
}

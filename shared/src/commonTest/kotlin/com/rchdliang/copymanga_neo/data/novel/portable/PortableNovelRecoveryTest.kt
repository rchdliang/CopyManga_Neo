package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.data.image.portable.ReaderImageMetadata
import com.rchdliang.copymanga_neo.data.novel.portable.cache.VerifiedPortableNovelCache
import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.data.remote.portable.NetworkSnapshot
import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlin.test.*

class PortableNovelRecoveryTest {
    @Test fun publicDetailAndDirectoryFallbackAreSharedAcrossAccountsAndProtocols() = runTest {
        val files = Files()
        val cache = VerifiedPortableNovelCache(files)
        var offline = false
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        val engine = HttpClient(MockEngine { request ->
            if (offline) throw com.rchdliang.copymanga_neo.domain.platform.PlatformIOException("offline")
            val result = if (request.url.encodedPath.endsWith("/volumes"))
                """{"list":[{"id":"v1","name":"Volume","index":1}]}"""
            else """{"book":{"path_word":"book","name":"Public title","author":[]}}"""
            respond("""{"code":200,"results":$result}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val identity = object : com.rchdliang.copymanga_neo.data.remote.portable.PortableOfficialIdentity {
            override val tapString = "synthetic-test-key"
            override val loginDate = "2026-10-28 12:00:00.000Z"
            override fun dateHeader() = "2026.10.28"
            override fun resolve(settings: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings) = settings.copy(
                pseudoId = "test-pseudo", umString = "test-um", device = "test-device", deviceInfo = "test-info", buildTime = "test-build")
        }
        val client = CopyMangaClient(engine, state, identity = identity)
        try {
            val repository = PortableNovelRepository(client, cache)
            val book = repository.detail(RequestMode.WEB, "guest", "book").getOrThrow()
            val volumes = repository.volumes(RequestMode.WEB, "guest", "book").getOrThrow()
            offline = true
            state.update { it.copy(settings = it.settings.copy(requestMode = RequestMode.OFFICIAL_APP),
                appSession = com.rchdliang.copymanga_neo.data.remote.portable.PortableSession("token", "user:another", "another")) }
            assertEquals(book, repository.detail(RequestMode.OFFICIAL_APP, "user:another", "book").getOrThrow())
            assertEquals(volumes, repository.volumes(RequestMode.OFFICIAL_APP, "user:another", "book").getOrThrow())
            assertEquals(2, files.list("novel/metadata").size)
        } finally { client.close(); engine.close() }
    }
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
            val client = CopyMangaClient(engine, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
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
        val client = CopyMangaClient(engine, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
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
        val client = CopyMangaClient(engine, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
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

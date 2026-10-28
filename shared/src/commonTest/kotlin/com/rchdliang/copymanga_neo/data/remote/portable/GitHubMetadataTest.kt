package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class GitHubMetadataTest {
    @Test fun appReleasePreservesNotesFromTheSelectedStableCmpRelease() = runTest {
        val notes = "## 更新说明\n\n- 修复跨组阅读\n- **优化体验**\n\n[完整说明](https://example.com/notes)"
        val releases = buildJsonArray {
            fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, body: String = "其他版本") {
                add(buildJsonObject {
                    put("tag_name", tag); put("draft", draft); put("prerelease", prerelease); put("body", body)
                })
            }
            release("v99.0", draft = true)
            release("v98.0", prerelease = true)
            release("android-v97.0")
            release("v0.10.25") // Legacy Android tag.
            release("v0.10.29", body = notes)
        }
        val http = HttpClient(MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            respond(releases.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, PortableNetworkState())
        try {
            val release = GitHubMetadata(client).latestApp()
            assertEquals("0.10.29", release.version)
            assertEquals(notes, release.releaseNotes)
            assertTrue(release.pageUrl.endsWith("/releases/tag/v0.10.29"))
        } finally { client.close(); http.close() }
    }

    @Test fun missingNullAndEmptyReleaseNotesStillAllowUpdateChecking() = runTest {
        for (body in listOf(null, JsonNull, JsonPrimitive(""))) {
            val payload = buildJsonArray { add(buildJsonObject {
                put("tag_name", "v0.10.29"); put("draft", false); put("prerelease", false)
                if (body != null) put("body", body)
            }) }
            val http = HttpClient(MockEngine {
                respond(payload.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
            val client = CopyMangaClient(http, PortableNetworkState())
            try { assertEquals("", GitHubMetadata(client).latestApp().releaseNotes) }
            finally { client.close(); http.close() }
        }
    }

    @Test fun publicRelease404IsNotReportedAsLatest() = runTest {
        val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })
        val client = CopyMangaClient(http, PortableNetworkState())
        try { assertFailsWith<PublicReleaseUnavailableException> { GitHubMetadata(client).latestApp() } }
        finally { client.close(); http.close() }
    }

    @Test fun stableReleaseUsesPinnedCommitAndDoesNotSendCredentials() = runTest {
        val revision = "0".repeat(40)
        var requests = 0
        val http = HttpClient(MockEngine { request ->
            requests++
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            val body = when {
                request.url.encodedPath.endsWith("/releases/latest") -> """{"draft":false,"prerelease":false,"tag_name":"ver.1.4.2"}"""
                "/commits/" in request.url.encodedPath -> """{"sha":"$revision"}"""
                else -> """{"truncated":false,"tree":[{"path":"data/dictionary/TSCharacters.txt","type":"blob","sha":"${"1".repeat(40)}","size":104516}]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, PortableNetworkState())
        try {
            val release = GitHubMetadata(client).latestDictionary()
            assertEquals("1.4.2", release.version)
            assertEquals(revision, release.revision)
            assertEquals(104516, release.files.getValue("TSCharacters.txt").size)
            assertEquals(3, requests)
        } finally { client.close(); http.close() }
    }

    @Test fun prereleaseIsRejected() = runTest {
        val http = HttpClient(MockEngine {
            respond("""[{"draft":false,"prerelease":true,"tag_name":"v0.10.4"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, PortableNetworkState())
        try { assertFailsWith<PublicReleaseUnavailableException> { GitHubMetadata(client).latestApp() } }
        finally { client.close(); http.close() }
    }
}

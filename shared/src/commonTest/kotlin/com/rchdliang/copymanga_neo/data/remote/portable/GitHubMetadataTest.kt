package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GitHubMetadataTest {
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
            respond("""{"draft":false,"prerelease":true,"tag_name":"v0.10.4"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, PortableNetworkState())
        try { assertFailsWith<IllegalArgumentException> { GitHubMetadata(client).latestApp() } }
        finally { client.close(); http.close() }
    }
}

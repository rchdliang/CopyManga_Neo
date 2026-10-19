package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ProfileSavedRefreshException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProfileUpdateTest {
    private class Fixture(val mode: RequestMode) {
        var gender = 1
        var avatar = "avatars/current.jpg"
        var nickname = "Original"
        var uploads = 0
        var saves = 0
        var reads = 0
        var rejectUpload = false
        var rejectSave = false
        var rejectRefresh = false
        val submitted = mutableListOf<Parameters>()
        private val identity = object : PortableOfficialIdentity {
            override val tapString = "synthetic-profile-test"
            override val loginDate = "2026-10-19"
            override fun dateHeader() = "2026.10.19"
            override fun resolve(settings: OfficialAppSettings) = settings.copy(
                pseudoId = "testProfileId000", umString = "0123456789abcdef0123456789abcdef",
                device = "test-device", deviceInfo = "test-model", buildTime = "test-build")
        }
        private val session = PortableSession("synthetic-profile-token", "user:tester", "tester")
        private val state = PortableNetworkState(NetworkSnapshot(
            settings = NetworkSettings(requestMode = mode), webSession = session, appSession = session))
        val http = HttpClient(MockEngine { request ->
            assertEquals("Token synthetic-profile-token", request.headers[HttpHeaders.Authorization])
            val json = when (request.url.encodedPath) {
                "/api/v3/member/update/info" -> {
                    assertEquals(if (mode == RequestMode.OFFICIAL_APP) "3" else null, request.url.parameters["platform"])
                    if (request.method == HttpMethod.Get) {
                        reads++
                        """{"code":200,"results":{"info":{"nickname":"$nickname","avatar":"$avatar","avatar_rp":"$avatar","gender":{"value":$gender,"display":"Current"}}}}"""
                    } else {
                        assertEquals(HttpMethod.Post, request.method)
                        saves++
                        val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        submitted += form
                        if (rejectSave) """{"code":400,"message":"资料修改受限"}"""
                        else {
                            nickname = form["nickname"]!!
                            avatar = form["avatar"]!!
                            gender = form["gender"]!!.toInt()
                            """{"code":200}"""
                        }
                    }
                }
                "/api/v3/upload/image/1" -> {
                    uploads++
                    assertEquals(HttpMethod.Post, request.method)
                    if (rejectUpload) """{"code":400,"message":"图片被拒绝","results":"avatars/rejected.jpg"}"""
                    else """{"code":200,"results":"avatars/new.jpg"}"""
                }
                "/api/v3/member/info" -> {
                    if (rejectRefresh) respondError(HttpStatusCode.ServiceUnavailable).let { return@MockEngine it }
                    """{"code":200,"results":{"username":"tester","nickname":"$nickname","avatar":"$avatar"}}"""
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, state, identity = identity)
        val repository = PortableUserRepository(client)
        fun close() { client.close(); http.close() }
    }

    @Test fun genderAndNicknameUpdatesKeepCurrentAvatarInBothModes() = runTest {
        for (mode in RequestMode.entries) {
            val fixture = Fixture(mode)
            try {
                val info = fixture.repository.updateProfile(" New name ", null, 2).getOrThrow()
                assertEquals("New name", info.nickname)
                assertEquals("2", fixture.submitted.single()["gender"])
                assertEquals("avatars/current.jpg", fixture.submitted.single()["avatar"])
                assertEquals(0, fixture.uploads)
                assertEquals(1, fixture.saves)
            } finally { fixture.close() }
        }
    }

    @Test fun untouchedGenderUsesLatestServerValue() = runTest {
        val fixture = Fixture(RequestMode.WEB)
        try {
            fixture.gender = 2
            fixture.repository.updateProfile("Changed", null, null).getOrThrow()
            assertEquals("2", fixture.submitted.single()["gender"])
        } finally { fixture.close() }
    }

    @Test fun selectedAvatarUploadsOnceAndUsesReturnedPathInBothModes() = runTest {
        for (mode in RequestMode.entries) {
            val fixture = Fixture(mode)
            try {
                val info = fixture.repository.updateProfile("Original", byteArrayOf(1, 2, 3), 0).getOrThrow()
                assertEquals("avatars/new.jpg", info.avatar)
                assertEquals("avatars/new.jpg", fixture.submitted.single()["avatar"])
                assertEquals("0", fixture.submitted.single()["gender"])
                assertEquals(1, fixture.uploads)
                assertEquals(1, fixture.saves)
            } finally { fixture.close() }
        }
    }

    @Test fun uploadBusinessFailureDoesNotAttemptProfileSave() = runTest {
        val fixture = Fixture(RequestMode.OFFICIAL_APP)
        try {
            fixture.rejectUpload = true
            val failure = fixture.repository.updateProfile("Changed", byteArrayOf(1), 2).exceptionOrNull()
            assertEquals("图片被拒绝", failure?.message)
            assertEquals(1, fixture.uploads)
            assertEquals(0, fixture.saves)
        } finally { fixture.close() }
    }

    @Test fun cancelledAvatarChoiceAfterSaveFailureDoesNotReuseUnsavedUpload() = runTest {
        val fixture = Fixture(RequestMode.WEB)
        try {
            fixture.rejectSave = true
            assertEquals("资料修改受限", fixture.repository.updateProfile("Changed", byteArrayOf(1), 2).exceptionOrNull()?.message)
            fixture.rejectSave = false
            fixture.repository.updateProfile("Changed", null, 2).getOrThrow()
            assertEquals("avatars/current.jpg", fixture.submitted.last()["avatar"])
            assertEquals(1, fixture.uploads)
        } finally { fixture.close() }
    }

    @Test fun savedButRefreshFailedHasDistinctOutcomeAndDoesNotReplayWrite() = runTest {
        val fixture = Fixture(RequestMode.WEB)
        try {
            fixture.rejectRefresh = true
            assertIs<ProfileSavedRefreshException>(fixture.repository.updateProfile("Changed", null, 2).exceptionOrNull())
            assertEquals(1, fixture.saves)
        } finally { fixture.close() }
    }

    @Test fun invalidGenderDoesNotSendRequests() = runTest {
        val fixture = Fixture(RequestMode.WEB)
        try {
            assertTrue(fixture.repository.updateProfile("Changed", null, 99).isFailure)
            assertEquals(0, fixture.reads)
            assertEquals(0, fixture.saves)
        } finally { fixture.close() }
    }
}

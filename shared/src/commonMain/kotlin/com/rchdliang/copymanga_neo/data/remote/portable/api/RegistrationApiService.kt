package com.rchdliang.copymanga_neo.data.remote.portable.api

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/** Portable implementation of the pinned upstream endpoint contract. */
class RegistrationApiService(private val client: CopyMangaClient, private val www: Boolean = false) {
    suspend fun registerWeb(
        username: String,
        rawPassword: String,
        source: String = "freeSite",
        version: String = CopyMangaNetworkConfig.WEB_VERSION,
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<RegistrationResponse> = client.call(ApiRequest(path = "/api/v3/register", method = "POST", query = emptyList(), form = listOfNotNull("username" to username, "password" to rawPassword, "source" to source, "version" to version, "platform" to platform.toString()), headers = headers, www = www, mode = null, appIdentity = null), serializer<RegistrationResponse>())

    suspend fun register(
        username: String,
        rawPassword: String,
        mobile: String = "",
        code: String = "",
        inviteCode: String = "",
        question: String = "",
        answer: String = "",
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<RegistrationResponse> = client.call(ApiRequest(path = "/api/v3/register", method = "POST", query = emptyList(), form = listOfNotNull("username" to username, "password" to rawPassword, "mobile" to mobile, "code" to code, "invite_code" to inviteCode, "question" to question, "answer" to answer), headers = headers, www = www, mode = null, appIdentity = null), serializer<RegistrationResponse>())
}

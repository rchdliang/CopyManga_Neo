package com.rchdliang.copymanga_neo.data.local

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets
import java.util.Base64

/** 可跨设备传递的登录凭据。凭据中的 token 等同于登录权限，必须按敏感信息处理。 */
internal data class LoginCredential(
    val token: String,
    val username: String
)

/**
 * CopyManga Neo 登录凭据编解码器。
 *
 * 外层使用固定前缀和 URL-safe Base64，内层使用带稳定字段名的 JSON，便于未来按版本迁移。
 */
internal object LoginCredentialCodec {
    private const val CREDENTIAL_PREFIX = "copymanga-neo:"
    private const val VERSIONED_PREFIX = "${CREDENTIAL_PREFIX}v1:"
    private const val MAX_SERIALIZED_LENGTH = 16_384
    private const val MAX_TOKEN_LENGTH = 8_192
    private const val MAX_USERNAME_LENGTH = 256

    private val gson = Gson()

    fun encode(token: String, username: String): String {
        validate(token = token, username = username)
        val payload = CredentialPayload(token = token, username = username)
        val json = gson.toJson(payload)
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return VERSIONED_PREFIX + encoded
    }

    fun decode(serialized: String): LoginCredential {
        val normalized = serialized.trim()
        require(normalized.isNotEmpty()) { "剪切板中没有登录凭据" }
        require(normalized.length <= MAX_SERIALIZED_LENGTH) { "登录凭据过长" }
        require(normalized.startsWith(VERSIONED_PREFIX)) {
            if (normalized.startsWith(CREDENTIAL_PREFIX)) {
                "不支持的登录凭据版本"
            } else {
                "不是 CopyManga Neo 登录凭据"
            }
        }

        val encodedPayload = normalized.removePrefix(VERSIONED_PREFIX)
        require(encodedPayload.isNotEmpty()) { "登录凭据格式无效" }

        val payload = try {
            val jsonBytes = Base64.getUrlDecoder().decode(encodedPayload)
            val json = String(jsonBytes, StandardCharsets.UTF_8)
            gson.fromJson(json, CredentialPayload::class.java)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("登录凭据格式无效")
        } catch (_: JsonParseException) {
            throw IllegalArgumentException("登录凭据格式无效")
        }

        val token = payload?.token ?: throw IllegalArgumentException("登录凭据缺少 token")
        val username = payload.username ?: throw IllegalArgumentException("登录凭据缺少用户名")
        validate(token = token, username = username)
        return LoginCredential(token = token, username = username)
    }

    private fun validate(token: String, username: String) {
        require(token.isNotBlank()) { "登录凭据缺少 token" }
        require(token.length <= MAX_TOKEN_LENGTH) { "登录 token 过长" }
        require(username.isNotBlank()) { "登录凭据缺少用户名" }
        require(username.length <= MAX_USERNAME_LENGTH) { "登录用户名过长" }
    }

    private data class CredentialPayload(
        @SerializedName("token") val token: String?,
        @SerializedName("username") val username: String?
    )
}

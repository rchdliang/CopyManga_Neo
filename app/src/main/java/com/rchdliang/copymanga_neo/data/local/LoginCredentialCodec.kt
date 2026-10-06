package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import java.nio.charset.StandardCharsets
import java.util.Base64

/** 每项凭据明确绑定请求模式，不跨模式复用 Token。 */
internal data class LoginCredential(val mode: RequestMode, val token: String, val username: String)
internal data class LoginCredentialBundle(
    val activeMode: RequestMode,
    val sessions: List<LoginCredential>
)

/** V2 包保存一种或两种模式的 Token 和用户名，不包含密码，旧包不迁移。 */
internal object LoginCredentialCodec {
    private const val PREFIX = "copymanga-neo:v2:"
    private const val MAX_LENGTH = 32_768

    fun encode(bundle: LoginCredentialBundle): String {
        validate(bundle)
        val root = JsonObject().apply {
            addProperty("active_mode", bundle.activeMode.name)
            add("sessions", JsonArray().apply {
                bundle.sessions.forEach { session ->
                    add(JsonObject().apply {
                        addProperty("mode", session.mode.name)
                        addProperty("token", session.token)
                        addProperty("username", session.username)
                    })
                }
            })
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(root.toString().toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(serialized: String): LoginCredentialBundle {
        val normalized = serialized.trim()
        require(normalized.isNotEmpty()) { "剪切板中没有登录凭据" }
        require(normalized.length <= MAX_LENGTH) { "登录凭据过长" }
        require(normalized.startsWith(PREFIX)) { "凭据格式不支持，请使用新版重新导出" }
        val bundle = try {
            val root = JsonParser.parseString(
                String(
                    Base64.getUrlDecoder().decode(normalized.removePrefix(PREFIX)),
                    StandardCharsets.UTF_8
                )
            ).asJsonObject
            val array = root.getAsJsonArray("sessions")
            require(array.size() in 1..RequestMode.entries.size)
            LoginCredentialBundle(mode(root, "active_mode"), array.map { element ->
                val item = element.asJsonObject
                LoginCredential(mode(item, "mode"), string(item, "token"), string(item, "username"))
            })
        } catch (_: RuntimeException) {
            throw IllegalArgumentException("登录凭据格式无效")
        }
        validate(bundle)
        return bundle
    }

    private fun string(root: JsonObject, key: String): String {
        val value = root.getAsJsonPrimitive(key)
        require(value.isString)
        return value.asString
    }

    private fun mode(root: JsonObject, key: String): RequestMode {
        val name = string(root, key)
        return RequestMode.entries.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("未知请求模式")
    }

    private fun validate(bundle: LoginCredentialBundle) {
        require(bundle.sessions.size in 1..RequestMode.entries.size) { "登录凭据模式数量无效" }
        require(bundle.sessions.map { it.mode }
            .distinct().size == bundle.sessions.size) { "登录凭据模式重复" }
        require(bundle.sessions.any { it.mode == bundle.activeMode }) { "登录凭据缺少当前模式" }
        bundle.sessions.forEach {
            require(it.token.isNotBlank() && it.token.length <= 8_192) { "登录 Token 无效" }
            require(it.username.isNotBlank() && it.username.length <= 256) { "登录用户名无效" }
        }
    }
}

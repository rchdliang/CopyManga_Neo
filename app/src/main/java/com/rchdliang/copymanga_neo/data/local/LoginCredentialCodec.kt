package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.AppCredentialIdentity
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import java.nio.charset.StandardCharsets
import java.util.Base64

/** 每项凭据明确绑定请求模式，不跨模式复用 Token。 */
internal data class LoginCredential(val mode: RequestMode, val token: String, val username: String)

/** 仅在用户勾选导出密码时附带，不用于自动恢复会话。 */
internal class SavedLoginCredential(val username: String, val password: String)
internal data class LoginCredentialBundle(
    val activeMode: RequestMode,
    val sessions: List<LoginCredential>,
    val appIdentity: AppCredentialIdentity? = null,
    val savedLogin: SavedLoginCredential? = null
)

/** V3 新导出只含当前模式及 App 请求身份；保留读取旧 V2/V3 多模式包的兼容性。 */
internal object LoginCredentialCodec {
    private const val PREFIX = "copymanga-neo:v3:"
    private const val LEGACY_PREFIX = "copymanga-neo:v2:"
    private const val MAX_LENGTH = 32_768

    fun encode(bundle: LoginCredentialBundle): String {
        validate(bundle)
        require(bundle.sessions.size == 1) { "只能导出当前模式的登录凭据" }
        require(bundle.sessions.none { it.mode == RequestMode.OFFICIAL_APP } || bundle.appIdentity != null) {
            "App 凭据缺少请求身份"
        }
        val root = JsonObject().apply {
            addProperty("active_mode", bundle.activeMode.name)
            bundle.savedLogin?.let { login ->
                add("saved_login", JsonObject().apply {
                    addProperty("username", login.username)
                    addProperty("password", login.password)
                })
            }
            bundle.appIdentity?.let { identity ->
                add("app_identity", JsonObject().apply {
                    addProperty("version", identity.version)
                    addProperty("version_code", identity.versionCode)
                    addProperty("pseudo_id", identity.pseudoId)
                    addProperty("um_string", identity.umString)
                    addProperty("device", identity.device)
                    addProperty("device_info", identity.deviceInfo)
                    addProperty("build_time", identity.buildTime)
                })
            }
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
            .encodeToString(root.toString().toByteArray(StandardCharsets.UTF_8)).also {
                require(PREFIX.length + it.length <= MAX_LENGTH) { "登录凭据过长" }
            }
    }

    fun decode(serialized: String): LoginCredentialBundle {
        val normalized = serialized.trim()
        require(normalized.isNotEmpty()) { "剪切板中没有登录凭据" }
        require(normalized.length <= MAX_LENGTH) { "登录凭据过长" }
        val prefix = listOf(PREFIX, LEGACY_PREFIX).firstOrNull(normalized::startsWith)
        require(prefix != null) { "凭据格式不支持，请使用新版重新导出" }
        val bundle = try {
            val root = JsonParser.parseString(
                String(
                    Base64.getUrlDecoder().decode(normalized.removePrefix(prefix)),
                    StandardCharsets.UTF_8
                )
            ).asJsonObject
            val array = root.getAsJsonArray("sessions")
            require(array.size() in 1..RequestMode.entries.size)
            val identity = if (prefix == PREFIX && root.has("app_identity")) {
                val item = root.getAsJsonObject("app_identity")
                AppCredentialIdentity(
                    string(item, "version"), string(item, "version_code"),
                    string(item, "pseudo_id"), string(item, "um_string"),
                    string(item, "device"), string(item, "device_info"), string(item, "build_time")
                )
            } else null
            val savedLogin = if (prefix == PREFIX && root.has("saved_login")) {
                val item = root.getAsJsonObject("saved_login")
                SavedLoginCredential(string(item, "username"), string(item, "password"))
            } else null
            LoginCredentialBundle(mode(root, "active_mode"), array.map { element ->
                val item = element.asJsonObject
                LoginCredential(mode(item, "mode"), string(item, "token"), string(item, "username"))
            }, identity, savedLogin).also {
                if (prefix == PREFIX && it.sessions.any { session -> session.mode == RequestMode.OFFICIAL_APP }) {
                    require(identity != null) { "App 凭据缺少请求身份" }
                }
            }
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
        bundle.savedLogin?.let {
            require(bundle.sessions.size == 1) { "含密码的凭据必须只包含当前模式" }
            require(it.username.isNotBlank() && it.username.length <= 256) { "登录账号无效" }
            require(it.password.isNotBlank() && it.password.length <= 4_096) { "登录密码无效" }
        }
        bundle.appIdentity?.let { identity ->
            require(bundle.sessions.any { it.mode == RequestMode.OFFICIAL_APP }) { "网页凭据不应包含 App 身份" }
            require(identity.version.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "App 请求版本格式无效" }
            require(identity.versionCode.toIntOrNull()?.let { it > 0 } == true) { "App 版本号无效" }
            listOf(
                identity.pseudoId,
                identity.umString,
                identity.device,
                identity.deviceInfo,
                identity.buildTime
            )
                .forEach { value ->
                    require(value.isNotBlank() && value.length <= 256 && value.all { it.code in 32..126 }) {
                        "App 请求身份格式无效"
                    }
                }
        }
        bundle.sessions.forEach {
            require(it.token.isNotBlank() && it.token.length <= 8_192) { "登录 Token 无效" }
            require(it.username.isNotBlank() && it.username.length <= 256) { "登录用户名无效" }
        }
    }
}

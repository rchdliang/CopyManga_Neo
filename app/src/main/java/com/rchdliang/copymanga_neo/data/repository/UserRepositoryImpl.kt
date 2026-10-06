package com.rchdliang.copymanga_neo.data.repository

import android.util.Base64
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.ImportedSession
import com.rchdliang.copymanga_neo.data.local.LoginCredentialCodec
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.LoginInfo
import com.rchdliang.copymanga_neo.domain.model.MemberInfo
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 用户仓库实现
 * 处理登录流程：生成盐、编码密码、调用API、保存token
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiService: MangaApiService,
    private val authDataStore: AuthDataStore,
    private val registrationApi: com.rchdliang.copymanga_neo.data.remote.api.RegistrationApiService,
    private val networkSettings: com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore,
    private val memberWrites: com.rchdliang.copymanga_neo.data.remote.api.MemberWriteApiService,
    private val profileApi: com.rchdliang.copymanga_neo.data.remote.api.ProfileApiService
) : UserRepository {

    override suspend fun getEditableProfile(): Result<com.rchdliang.copymanga_neo.domain.model.EditableProfile> =
        try {
            val token = authDataStore.getTokenSync() ?: throw IllegalStateException("请先登录")
            val generation = authDataStore.getAuthGeneration()
            val networkGeneration = networkSettings.generation
            val response = profileApi.editable("Token $token")
            checkProfileResponse(response, response.body()?.code, response.body()?.message)
            check(authDataStore.getAuthGeneration() == generation && networkSettings.generation == networkGeneration) {
                "会话或网络设置已变化，请重新打开资料编辑"
            }
            val info = response.body()?.results?.info ?: throw IllegalStateException("账号资料为空")
            Result.success(
                com.rchdliang.copymanga_neo.domain.model.EditableProfile(
                    info.nickname.orEmpty(), info.avatar, info.avatarPath, info.gender ?: 0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun updateProfile(
        nickname: String,
        avatarJpeg: ByteArray?
    ): Result<MemberInfo> = try {
        require(nickname.isNotBlank()) { "请输入昵称" }
        val token = authDataStore.getTokenSync() ?: throw IllegalStateException("请先登录")
        val generation = authDataStore.getAuthGeneration()
        val networkGeneration = networkSettings.generation
        fun checkSession() {
            check(
                authDataStore.getAuthGeneration() == generation &&
                        authDataStore.getTokenSync() == token && networkSettings.generation == networkGeneration
            ) {
                "会话或网络设置已变化，请重新打开资料编辑"
            }
        }

        val currentResponse = profileApi.editable("Token $token")
        checkProfileResponse(
            currentResponse,
            currentResponse.body()?.code,
            currentResponse.body()?.message
        )
        checkSession()
        val current =
            currentResponse.body()?.results?.info ?: throw IllegalStateException("账号资料为空")
        val avatarPath = if (avatarJpeg != null) {
            require(avatarJpeg.isNotEmpty() && avatarJpeg.size <= 5 * 1024 * 1024) { "头像文件为空或过大" }
            val part = okhttp3.MultipartBody.Part.createFormData(
                "file", "avatar.jpg",
                avatarJpeg.toRequestBody("image/jpeg".toMediaType())
            )
            val upload = profileApi.uploadAvatar(part, "Token $token")
            checkProfileResponse(upload, upload.body()?.code, upload.body()?.message)
            checkSession()
            upload.body()?.path?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("头像上传未返回路径")
        } else current.avatarPath ?: current.avatar.orEmpty()
        checkSession()
        val saved =
            profileApi.update(nickname.trim(), avatarPath, current.gender ?: 0, "Token $token")
        checkProfileResponse(saved, saved.body()?.code, saved.body()?.message)
        try {
            checkSession()
            val refreshed = getMemberInfo()
            refreshed.fold(
                onSuccess = { checkSession(); Result.success(it) },
                onFailure = {
                    Result.failure(
                        com.rchdliang.copymanga_neo.domain.repository.ProfileSavedRefreshException(
                            "资料已保存，但刷新失败，请关闭后刷新账号页", it
                        )
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                com.rchdliang.copymanga_neo.domain.repository.ProfileSavedRefreshException(
                    "资料已保存，会话或网络已变化，请重新查看", e
                )
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun checkProfileResponse(
        response: retrofit2.Response<*>,
        code: Int?,
        message: String?
    ) {
        if (response.isSuccessful && code == 200) return
        val error = response.errorBody()?.use { it.string() }
        val detail = message ?: runCatching {
            com.google.gson.JsonParser.parseString(error).asJsonObject.get("message")?.asString
        }.getOrNull()
        throw IllegalStateException(detail ?: "账号资料请求失败（HTTP ${response.code()}）")
    }

    override suspend fun register(username: String, rawPassword: String):
            Result<com.rchdliang.copymanga_neo.domain.model.RegistrationInfo> {
        return try {
            com.rchdliang.copymanga_neo.domain.model.RegistrationPolicy
                .validationError(username, rawPassword)
                ?.let { return Result.failure(IllegalArgumentException(it)) }
            check(!authDataStore.isLoggedInSync()) { "请先退出当前账号后注册" }
            val (generation, settings) = networkSettings.snapshot()
            val authGeneration = authDataStore.getAuthGeneration()
            // 注册直接发送原始密码；登录另行使用 Base64(密码-盐)，两者不能混用。
            val response = when (settings.requestMode) {
                RequestMode.WEB ->
                    registrationApi.registerWeb(username, rawPassword, version = settings.version)

                RequestMode.OFFICIAL_APP ->
                    registrationApi.register(username, rawPassword)
            }
            val body = response.body()
            if (!response.isSuccessful || body?.code != 200) {
                val error = response.errorBody()?.use { it.string() }
                val message = body?.message ?: runCatching {
                    com.google.gson.JsonParser.parseString(error).asJsonObject
                        .get("message")?.takeUnless { it.isJsonNull }?.asString
                }.getOrNull()
                return Result.failure(Exception(message ?: "注册失败（HTTP ${response.code()}）"))
            }
            check(body.results != null) { "注册响应缺少账号信息，请先尝试登录，勿重复注册" }
            Result.success(
                com.rchdliang.copymanga_neo.domain.model.RegistrationInfo(
                    username, networkSettings.generation == generation &&
                            authDataStore.getAuthGeneration() == authGeneration
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(
        username: String,
        rawPassword: String,
        expectedGeneration: Long?
    ): Result<LoginInfo> =
        try {
            authDataStore.awaitReady()
            val generation = authDataStore.getAuthGeneration()
            val (networkGeneration, settings) = networkSettings.snapshot()
            check(expectedGeneration == null || expectedGeneration == generation) { "登录期间会话已改变" }
            val result = requestLogin(username, rawPassword, settings.requestMode)
            check(authDataStore.getAuthGeneration() == generation && networkSettings.generation == networkGeneration) {
                "登录期间会话或网络设置已改变"
            }
            result.fold(
                onSuccess = { info ->
                    val token = info.token
                        ?: return@fold Result.failure(IllegalStateException("未能获取登录 Token"))
                    val saved = authDataStore.saveAuthIfCurrent(
                        generation, token, info.username ?: username, info.avatar
                    )
                    if (saved) Result.success(info) else Result.failure(IllegalStateException("登录期间会话已改变"))
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun loginWithOptions(
        username: String,
        rawPassword: String,
        bothModes: Boolean,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        expectedGeneration: Long
    ): Result<com.rchdliang.copymanga_neo.domain.model.ModeLoginResult> = try {
        authDataStore.awaitReady()
        val (networkGeneration, settings) = networkSettings.snapshot()
        fun checkSession() {
            check(
                authDataStore.getAuthGeneration() == expectedGeneration &&
                        networkSettings.generation == networkGeneration
            ) { "登录期间会话或网络设置已改变" }
        }
        checkSession()
        val modes = if (bothModes) listOf(settings.requestMode) +
                RequestMode.entries.filter { it != settings.requestMode } else listOf(settings.requestMode)
        val successful = mutableListOf<ImportedSession>()
        val failures = linkedMapOf<RequestMode, String>()
        for (mode in modes) {
            checkSession()
            requestLogin(username, rawPassword, mode).fold(
                onSuccess = { info ->
                    successful += ImportedSession(
                        mode,
                        requireNotNull(info.token),
                        info.username ?: username,
                        info.avatar
                    )
                },
                onFailure = { failures[mode] = it.message ?: "登录失败" }
            )
            checkSession()
        }
        if (successful.isEmpty()) {
            val message = failures.entries.joinToString("；") { (mode, reason) ->
                "${if (mode == RequestMode.WEB) "网页模式" else "App 模式"}登录失败：$reason"
            }
            Result.failure(IllegalStateException(message))
        } else {
            val saved = authDataStore.saveLoginSessionsIfCurrent(
                expectedGeneration, successful, username, rawPassword, rememberPassword, autoLogin
            )
            check(saved) { "登录期间会话已改变" }
            Result.success(
                com.rchdliang.copymanga_neo.domain.model.ModeLoginResult(
                    settings.requestMode, successful.map { it.mode }.toSet(), failures
                )
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 固定目标模式，各自取得 Token；不切换全局模式，不在请求中输出原始密码。 */
    private suspend fun requestLogin(
        username: String,
        rawPassword: String,
        mode: RequestMode
    ): Result<LoginInfo> = try {
        val salt = Random.nextInt(1000, 10000).toString()
        val encodedPassword =
            Base64.encodeToString("$rawPassword-$salt".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val response = apiService.login(
            username = username, password = encodedPassword, salt = salt, requestMode = mode
        )
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}: ${response.message()}")
        val body = response.body() ?: throw IllegalStateException("登录响应为空")
        if (body.code != 200) throw IllegalStateException(
            body.message ?: "登录失败(code=${body.code})"
        )
        val info = body.results ?: throw IllegalStateException("登录结果为空")
        val token = info.token?.takeIf { it.isNotBlank() }
            ?: extractTokenFromCookieHeaders(response.headers())
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("未能获取登录 Token")
        Result.success(info.toLoginInfo().copy(token = token))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun exportLoginCredential(): Result<String> = try {
        authDataStore.awaitReady()
        Result.success(LoginCredentialCodec.encode(authDataStore.exportSessions()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun importLoginCredential(serializedCredential: String): Result<MemberInfo> =
        try {
            authDataStore.awaitReady()
            val generation = authDataStore.getAuthGeneration()
            val (networkGeneration, settings) = networkSettings.snapshot()
            val bundle = LoginCredentialCodec.decode(serializedCredential)
            val selectedMode = settings.requestMode.takeIf { mode ->
                bundle.sessions.any { it.mode == mode }
            } ?: bundle.activeMode
            val imported = mutableListOf<ImportedSession>()
            var selectedInfo: MemberInfo? = null
            for (credential in bundle.sessions) {
                check(
                    networkSettings.generation == networkGeneration &&
                            authDataStore.getAuthGeneration() == generation
                ) { "导入期间会话或网络设置已改变" }
                // 每项通过所属模式校验，不暂时切换全局网络设置或串用另一模式 Token。
            val response = apiService.getMemberInfoWithCredential(
                authorization = "Token ${credential.token}",
                requestMode = credential.mode
            )
                if (response.code() == 401) throw IllegalStateException("登录凭据已失效，请重新导出")
                if (!response.isSuccessful) throw IllegalStateException("验证登录凭据失败: ${response.code()}")
                val body = response.body() ?: throw IllegalStateException("验证登录凭据的响应为空")
                if (body.code != 200) throw IllegalStateException(body.message ?: "登录凭据无效")
                val member = body.results ?: throw IllegalStateException("登录凭据未返回用户信息")
                imported += ImportedSession(
                    credential.mode, credential.token,
                    member.username ?: credential.username, member.avatar
            )
                if (credential.mode == selectedMode) selectedInfo = member.toMemberInfo()
            }
            check(networkSettings.generation == networkGeneration) { "导入期间网络设置已改变" }
            val saved = authDataStore.saveImportedSessions(generation, imported)
            check(saved) { "导入期间会话已改变" }
            check(authDataStore.getAuthGeneration() == generation + 1) { "导入期间会话已改变" }
            // 单模式包不包含当前模式时，自动进入包中的模式；双模式包保持当前选择。
            if (selectedMode != settings.requestMode) {
                check(
                    networkSettings.saveIfCurrent(
                        networkGeneration,
                        settings.copy(requestMode = selectedMode)
                    )
                ) {
                    "导入期间网络设置已改变"
            }
            }
            Result.success(selectedInfo ?: throw IllegalStateException("凭据未包含所选模式"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 不输出导入文本、Token 或原始响应。
            Result.failure(e)
    }

    override suspend fun getMemberInfo(): Result<MemberInfo> {
        return try {
            val authGeneration = authDataStore.getAuthGeneration()
            val token = authDataStore.getTokenSync()
                ?: return Result.failure(Exception("当前未登录"))
            val response = apiService.getMemberInfo()
            if (response.code() == 401) {
                // AuthInterceptor 已按请求所用 token 清除过期会话；不能把稍后登录的新会话退出。
                return Result.failure(Exception("会话已过期，请重新登录"))
            }

            if (!response.isSuccessful) {
                return Result.failure(Exception("获取用户信息失败: ${response.code()}"))
            }

            val body = response.body()
            if (body == null || body.code != 200) {
                return Result.failure(Exception(body?.message ?: "获取用户信息失败"))
            }

            val memberInfo = body.results ?: return Result.failure(Exception("用户信息为空"))

            val updated = authDataStore.updateMemberInfoIfCurrent(
                generation = authGeneration,
                token = token,
                username = memberInfo.username ?: memberInfo.nickname ?: "",
                avatar = memberInfo.avatar
            )
            if (!updated) return Result.failure(Exception("会话已改变，忽略旧的用户信息"))

            Result.success(memberInfo.toMemberInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "getMemberInfo exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val mode = authDataStore.getRequestModeSync()
            val token = authDataStore.getTokenForModeSync(mode)
            val appMode = mode == RequestMode.OFFICIAL_APP
            // 先完成本地退出并关闭自动登录；服务端响应不得清除稍后登录的新账号。
            authDataStore.clearAuth(manualLogout = true, mode = mode)
            if (appMode && !token.isNullOrBlank()) {
                try {
                    val response =
                        memberWrites.logout("Token $token", requestMode = RequestMode.OFFICIAL_APP)
                    if (!response.isSuccessful || response.body()?.code != 200) {
                        android.util.Log.w("UserRepository", "本地已退出，服务端退出未确认")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 无网络也必须允许退出，本地结果不依赖服务端可用性。
                    android.util.Log.w("UserRepository", "本地已退出，服务端退出请求失败")
                }
            }
            android.util.Log.d("UserRepository", "Logout success")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun sessionChanges(): Flow<Long> = authDataStore.sessionChanges

    override fun sessionVersionSync(): Long = authDataStore.getAuthGeneration()

    override fun isLoggedIn(): Flow<Boolean> = authDataStore.isLoggedIn()

    override fun isAuthCacheReady(): Flow<Boolean> = authDataStore.cacheReadyFlow

    override fun isLoggedInSync(): Boolean = authDataStore.isLoggedInSync()

    override fun getUsername(): Flow<String?> = authDataStore.getUsername()

    override fun getAvatar(): Flow<String?> = authDataStore.getAvatar()

    override suspend fun saveCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        expectedGeneration: Long
    ) {
        authDataStore.saveCredentials(
            username,
            password,
            rememberPassword,
            autoLogin,
            expectedGeneration
        )
    }

    override fun getRememberPassword(): Flow<Boolean> = authDataStore.getRememberPassword()

    override fun getAutoLogin(): Flow<Boolean> = authDataStore.getAutoLogin()

    override fun getSavedUsername(): Flow<String?> = authDataStore.getSavedUsername()

    override fun getSavedPassword(): Flow<String?> = authDataStore.getSavedPassword()

    override fun getAutoLoginSync(): Boolean = authDataStore.getAutoLoginSync()

    override fun getRememberPasswordSync(): Boolean = authDataStore.getRememberPasswordSync()

    override fun getSavedUsernameSync(): String? = authDataStore.getSavedUsernameSync()

    override fun getSavedPasswordSync(): String? = authDataStore.getSavedPasswordSync()

    /**
     * 从 response 的 set-cookie header 中提取 token
     * Cookie 格式可能为：token=xxx; Path=/; ...
     */
    private fun extractTokenFromCookieHeaders(headers: okhttp3.Headers): String? {
        for (cookieStr in headers.values("set-cookie")) {
            // 查找名为 "token" 的 cookie
            if (cookieStr.startsWith("token=")) {
                return cookieStr.substringAfter("token=").substringBefore(";")
            }
        }
        return null
    }
}

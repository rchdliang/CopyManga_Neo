package com.rchdliang.copymanga_neo.data.repository

import android.util.Base64
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.LoginCredentialCodec
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.di.PersistentCookieJar
import com.rchdliang.copymanga_neo.domain.model.LoginInfo
import com.rchdliang.copymanga_neo.domain.model.MemberInfo
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    private val cookieJar: PersistentCookieJar,
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
                com.rchdliang.copymanga_neo.domain.model.RequestMode.WEB ->
                    registrationApi.registerWeb(username, rawPassword, version = settings.version)

                com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP ->
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

    override suspend fun login(username: String, rawPassword: String): Result<LoginInfo> {
        return try {
            val authGeneration = authDataStore.getAuthGeneration()
            // 1. 生成随机4位盐
            val salt = Random.nextInt(1000, 10000).toString()

            // 2. 计算 base64 编码密码：Base64(rawPassword + "-" + salt)
            val passwordInput = "$rawPassword-$salt"
            val encodedPassword = Base64.encodeToString(
                passwordInput.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            // 3. 调用登录 API
            val response = apiService.login(
                username = username,
                password = encodedPassword,
                salt = salt
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("UserRepository", "Login HTTP error: $errorBody")
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body() ?: return Result.failure(Exception("登录响应为空"))

            if (body.code != 200) {
                return Result.failure(Exception(body.message ?: "登录失败(code=${body.code})"))
            }

            val loginResult = body.results ?: return Result.failure(Exception("登录结果为空"))

            // 4. 提取 token：优先从 JSON results 获取，如果为空则从 set-cookie header 获取
            val token = loginResult.token ?: extractTokenFromCookieHeaders(response.headers())

            if (token == null) {
                return Result.failure(Exception("未能获取登录 token"))
            }

            // 5. 保存 token、用户名和头像到 AuthDataStore
            val saved = authDataStore.saveAuthIfCurrent(
                generation = authGeneration,
                token = token,
                username = loginResult.username ?: username,
                avatar = loginResult.avatar,
                loginCredentials = username to rawPassword
            )
            if (!saved) {
                if (!authDataStore.isLoggedInSync()) cookieJar.clearAll()
                return Result.failure(Exception("登录期间会话已退出"))
            }

            android.util.Log.d("UserRepository", "Login success: username=${loginResult.username}")

            Result.success(loginResult.toLoginInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Login exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun exportLoginCredential(): Result<String> {
        return try {
            val token = authDataStore.getToken().firstOrNull()
                ?: return Result.failure(Exception("当前未登录，无法导出登录凭据"))
            val username = authDataStore.getUsername().firstOrNull()
                ?: return Result.failure(Exception("当前登录信息缺少用户名，无法导出"))
            Result.success(LoginCredentialCodec.encode(token = token, username = username))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Export credential failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun importLoginCredential(serializedCredential: String): Result<MemberInfo> {
        return try {
            val authGeneration = authDataStore.getAuthGeneration()
            val credential = LoginCredentialCodec.decode(serializedCredential)
            val response = apiService.getMemberInfoWithCredential(
                authorization = "Token ${credential.token}"
            )

            if (response.code() == 401) {
                return Result.failure(Exception("登录凭据已失效，请重新导出"))
            }
            if (!response.isSuccessful) {
                return Result.failure(Exception("验证登录凭据失败: ${response.code()}"))
            }

            val body = response.body()
                ?: return Result.failure(Exception("验证登录凭据的响应为空"))
            if (body.code != 200) {
                return Result.failure(Exception(body.message ?: "登录凭据无效"))
            }

            val memberInfo = body.results
                ?: return Result.failure(Exception("登录凭据未返回用户信息"))
            val saved = authDataStore.saveImportedAuth(
                generation = authGeneration,
                token = credential.token,
                username = memberInfo.username ?: memberInfo.nickname ?: credential.username,
                avatar = memberInfo.avatar
            )
            if (!saved) return Result.failure(Exception("导入期间会话已退出"))
            Result.success(memberInfo.toMemberInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Import credential failed: ${e.message}")
            Result.failure(e)
        }
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
            val token = authDataStore.getTokenSync()
            val appMode = networkSettings.settings.value.requestMode ==
                    com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP
            // 先完成本地退出并关闭自动登录；服务端响应不得清除稍后登录的新账号。
            authDataStore.clearAuth(manualLogout = true)
            cookieJar.clearAll()
            if (appMode && !token.isNullOrBlank()) {
                try {
                    val response = memberWrites.logout("Token $token")
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

    override fun isLoggedIn(): Flow<Boolean> = authDataStore.isLoggedIn()

    override fun isAuthCacheReady(): Flow<Boolean> = authDataStore.cacheReadyFlow

    override fun isLoggedInSync(): Boolean = authDataStore.isLoggedInSync()

    override fun getUsername(): Flow<String?> = authDataStore.getUsername()

    override fun getAvatar(): Flow<String?> = authDataStore.getAvatar()

    override suspend fun saveCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean
    ) {
        authDataStore.saveCredentials(username, password, rememberPassword, autoLogin)
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

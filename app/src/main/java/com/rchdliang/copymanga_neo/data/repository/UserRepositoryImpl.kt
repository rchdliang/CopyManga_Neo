package com.rchdliang.copymanga_neo.data.repository

import android.util.Base64
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.LoginResult
import com.rchdliang.copymanga_neo.data.remote.dto.MemberInfoResult
import com.rchdliang.copymanga_neo.di.PersistentCookieJar
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    private val cookieJar: PersistentCookieJar
) : UserRepository {

    override suspend fun login(username: String, rawPassword: String): Result<LoginResult> {
        return try {
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
            authDataStore.saveAuth(
                token = token,
                username = loginResult.username ?: username,
                avatar = loginResult.avatar
            )

            android.util.Log.d(
                "UserRepository",
                "Login success: username=${loginResult.username}, token=$token"
            )

            Result.success(loginResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Login exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getMemberInfo(): Result<MemberInfoResult> {
        return try {
            val response = apiService.getMemberInfo()
            if (response.code() == 401) {
                android.util.Log.w("UserRepository", "Session expired (401), clearing auth")
                logout()
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

            // 更新 AuthDataStore 中的信息，保持 token 不变
            val currentToken = authDataStore.getToken().firstOrNull() ?: ""
            authDataStore.saveAuth(
                token = currentToken,
                username = memberInfo.username ?: memberInfo.nickname ?: "",
                avatar = memberInfo.avatar
            )

            Result.success(memberInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "getMemberInfo exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            authDataStore.clearAuth()
            cookieJar.clearAll()
            android.util.Log.d("UserRepository", "Logout success")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isLoggedIn(): Flow<Boolean> = authDataStore.isLoggedIn()

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

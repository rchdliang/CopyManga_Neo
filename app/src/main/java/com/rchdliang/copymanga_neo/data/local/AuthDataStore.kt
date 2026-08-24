package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证数据持久化存储
 * 使用 DataStore<Preferences> 存储 token、用户名、头像、登录偏好和记住密码的账号密码。
 * 注意：密码以明文形式存入 DataStore。Android :app 的私有 DataStore 目录已被 OS 沙箱隔离，
 * 仅本应用可访问；如需更高安全级别，可改用 AndroidKeyStore + javax.crypto 包裹，
 * 但本工程目前不引入该实现以避免 androidx.security.crypto 的 deprecation warning。
 */
@Singleton
class AuthDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_AVATAR = stringPreferencesKey("auth_avatar")
        private val KEY_SAVED_USERNAME = stringPreferencesKey("saved_username")
        private val KEY_SAVED_PASSWORD = stringPreferencesKey("saved_password")
        private val KEY_REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        private val KEY_AUTO_LOGIN = booleanPreferencesKey("auto_login")

        // 旧版本曾用 EncryptedSharedPreferences 存储密码（key="saved_password"），现已移除该依赖。
        // 升级到新版本后，老用户的已记住密码不会自动迁移，首次启动会要求重新登录一次。
    }

    /** 缓存当前 token，供 OkHttp Interceptor 同步读取 */
    @Volatile
    private var cachedToken: String? = null

    /** 缓存当前 username */
    @Volatile
    private var cachedUsername: String? = null

    /** 缓存当前 avatar */
    @Volatile
    private var cachedAvatar: String? = null

    /** 缓存记住密码状态 */
    @Volatile
    private var cachedRememberPassword: Boolean = false

    /** 缓存自动登录状态 */
    @Volatile
    private var cachedAutoLogin: Boolean = false

    /** 缓存保存的用户名 */
    @Volatile
    private var cachedSavedUsername: String? = null

    /** 缓存保存的密码（从 DataStore 读取） */
    @Volatile
    private var cachedSavedPassword: String? = null

    /** 内部协程作用域，用于异步执行 DataStore 读写操作（避免在主线程 / Interceptor 中阻塞） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 缓存是否已完成首次预热。冷启动期间同步 API 会返回默认值（未登录 / null），预热完成后 Flow 会自动纠正 UI 状态。 */
    @Volatile
    private var cacheReady: Boolean = false

    private val _cacheReadyFlow = MutableStateFlow(false)
    val cacheReadyFlow: Flow<Boolean> = _cacheReadyFlow.asStateFlow()

    init {
        // 异步预热缓存：避免 runBlocking 阻塞构造线程（AuthDataStore 持有 @Singleton，构造可能位于关键路径）。
        // 预热期间同步取值返回默认 false/null，可接受；预热完后 UI 通过 isLoggedIn() 等 Flow 自动得到最新值。
        scope.launch {
            val prefs = dataStore.data.first()
            cachedToken = prefs[KEY_TOKEN]
            cachedUsername = prefs[KEY_USERNAME]
            cachedAvatar = prefs[KEY_AVATAR]
            cachedRememberPassword = prefs[KEY_REMEMBER_PASSWORD] ?: false
            cachedAutoLogin = prefs[KEY_AUTO_LOGIN] ?: false
            cachedSavedUsername = prefs[KEY_SAVED_USERNAME]
            cachedSavedPassword = prefs[KEY_SAVED_PASSWORD]
            cacheReady = true
            _cacheReadyFlow.value = true
            android.util.Log.d(
                "AuthDataStore",
                "Cache preloaded: token=${if (cachedToken != null) "***" else "null"}, autoLogin=$cachedAutoLogin"
            )
        }
    }

    /** 缓存是否已预热完成。仅供测试或诊断使用，业务代码应通过 Flow 观察 UI 状态。 */
    fun isCacheReady(): Boolean = cacheReady

    /** 保存 token、用户名和头像 */
    suspend fun saveAuth(token: String, username: String, avatar: String?) {
        cachedToken = token
        cachedUsername = username
        cachedAvatar = avatar
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
            if (avatar != null) {
                prefs[KEY_AVATAR] = avatar
            } else {
                prefs.remove(KEY_AVATAR)
            }
        }
    }

    /**
     * 保存从外部凭据验证得到的会话，并清除旧账号的记住密码与自动登录设置。
     * 认证状态和凭据偏好在同一次 DataStore edit 中更新，避免跨账号自动登录。
     */
    suspend fun saveImportedAuth(token: String, username: String, avatar: String?) {
        cachedToken = token
        cachedUsername = username
        cachedAvatar = avatar
        cachedRememberPassword = false
        cachedAutoLogin = false
        cachedSavedUsername = null
        cachedSavedPassword = null
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
            if (avatar != null) {
                prefs[KEY_AVATAR] = avatar
            } else {
                prefs.remove(KEY_AVATAR)
            }
            prefs[KEY_REMEMBER_PASSWORD] = false
            prefs[KEY_AUTO_LOGIN] = false
            prefs.remove(KEY_SAVED_USERNAME)
            prefs.remove(KEY_SAVED_PASSWORD)
        }
    }

    /**
     * 保存登录凭据偏好
     * @param username 用户名
     * @param password 原始密码（明文存入 DataStore，由 OS 沙箱隔离保护）
     * @param rememberPassword 是否记住密码
     * @param autoLogin 是否自动登录
     */
    suspend fun saveCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean
    ) {
        cachedRememberPassword = rememberPassword
        cachedAutoLogin = autoLogin
        if (rememberPassword) {
            cachedSavedUsername = username
            cachedSavedPassword = password
        } else {
            cachedSavedUsername = null
            cachedSavedPassword = null
        }
        dataStore.edit { prefs ->
            prefs[KEY_REMEMBER_PASSWORD] = rememberPassword
            prefs[KEY_AUTO_LOGIN] = autoLogin
            if (rememberPassword) {
                prefs[KEY_SAVED_USERNAME] = username
                prefs[KEY_SAVED_PASSWORD] = password
            } else {
                prefs.remove(KEY_SAVED_USERNAME)
                prefs.remove(KEY_SAVED_PASSWORD)
            }
        }
    }

    /** 获取 token（异步 Flow） */
    fun getToken(): Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_TOKEN] }

    /** 获取 token（同步，供 Interceptor 使用） */
    fun getTokenSync(): String? = cachedToken

    /** 获取 username（异步 Flow） */
    fun getUsername(): Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_USERNAME] }

    /** 获取 username（同步） */
    fun getUsernameSync(): String? = cachedUsername

    /** 获取 avatar（异步 Flow） */
    fun getAvatar(): Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_AVATAR] }

    /** 获取 avatar（同步） */
    fun getAvatarSync(): String? = cachedAvatar

    /** 获取记住密码状态（Flow） */
    fun getRememberPassword(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_REMEMBER_PASSWORD] ?: false }

    /** 获取记住密码状态（同步） */
    fun getRememberPasswordSync(): Boolean = cachedRememberPassword

    /** 获取自动登录状态（Flow） */
    fun getAutoLogin(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_AUTO_LOGIN] ?: false }

    /** 获取自动登录状态（同步） */
    fun getAutoLoginSync(): Boolean = cachedAutoLogin

    /** 获取保存的用户名（Flow） */
    fun getSavedUsername(): Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_SAVED_USERNAME] }

    /** 获取保存的用户名（同步） */
    fun getSavedUsernameSync(): String? = cachedSavedUsername

    /** 获取保存的密码（Flow，基于缓存，避免在 UiState 中持有明文密码） */
    fun getSavedPassword(): Flow<String?> = flowOf(cachedSavedPassword)

    /** 获取保存的密码（同步，从 DataStore 缓存读取） */
    fun getSavedPasswordSync(): String? = cachedSavedPassword

    /** 清除认证数据（登出） */
    suspend fun clearAuth() {
        cachedToken = null
        cachedUsername = null
        cachedAvatar = null
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_AVATAR)
        }
        // 登出时不清除记住密码/自动登录偏好，保留凭据
    }

    /** 同步清除认证缓存（供 OkHttp Interceptor 调用，避免 runBlocking） */
    fun clearAuthSync() {
        cachedToken = null
        cachedUsername = null
        cachedAvatar = null
        scope.launch {
            dataStore.edit { prefs ->
                prefs.remove(KEY_TOKEN)
                prefs.remove(KEY_USERNAME)
                prefs.remove(KEY_AVATAR)
            }
        }
    }

    /** 是否已登录（Flow） */
    fun isLoggedIn(): Flow<Boolean> = getToken().map { it != null }

    /** 是否已登录（同步，取缓存值） */
    fun isLoggedInSync(): Boolean = cachedToken != null
}

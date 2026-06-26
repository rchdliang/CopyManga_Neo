package com.rchdliang.copymanga_neo.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证数据持久化存储
 * 使用 DataStore<Preferences> 存储 token、用户名、头像和登录偏好
 * 密码使用 EncryptedSharedPreferences 加密存储，不以明文形式落盘
 */
@Singleton
class AuthDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptedPrefs: SharedPreferences
) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_AVATAR = stringPreferencesKey("auth_avatar")
        private val KEY_SAVED_USERNAME = stringPreferencesKey("saved_username")
        private val KEY_REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        private val KEY_AUTO_LOGIN = booleanPreferencesKey("auto_login")

        /** EncryptedSharedPreferences 中存储密码的键 */
        private const val ENCRYPTED_KEY_PASSWORD = "saved_password"
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

    /** 缓存保存的密码（从 EncryptedSharedPreferences 读取） */
    @Volatile
    private var cachedSavedPassword: String? = null

    /** 内部协程作用域，用于异步执行 DataStore 写入操作（避免在 Interceptor 中阻塞） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        runBlocking(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            cachedToken = prefs[KEY_TOKEN]
            cachedUsername = prefs[KEY_USERNAME]
            cachedAvatar = prefs[KEY_AVATAR]
            cachedRememberPassword = prefs[KEY_REMEMBER_PASSWORD] ?: false
            cachedAutoLogin = prefs[KEY_AUTO_LOGIN] ?: false
            cachedSavedUsername = prefs[KEY_SAVED_USERNAME]

            // 迁移：将 DataStore 中的明文密码迁移到 EncryptedSharedPreferences
            val legacyPasswordKey = stringPreferencesKey("saved_password")
            val legacyPassword = prefs[legacyPasswordKey]
            if (legacyPassword != null && encryptedPrefs.getString(ENCRYPTED_KEY_PASSWORD, null) == null) {
                encryptedPrefs.edit { putString(ENCRYPTED_KEY_PASSWORD, legacyPassword) }
                dataStore.edit { it.remove(legacyPasswordKey) }
            }
        }
        // 从 EncryptedSharedPreferences 读取密码到缓存
        cachedSavedPassword = encryptedPrefs.getString(ENCRYPTED_KEY_PASSWORD, null)
        android.util.Log.d(
            "AuthDataStore",
            "Init complete: token=${if (cachedToken != null) "***" else "null"}, autoLogin=$cachedAutoLogin"
        )
    }

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
     * 保存登录凭据偏好
     * @param username 用户名
     * @param password 原始密码（加密存储到 EncryptedSharedPreferences）
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
            // 密码加密存储，不以明文形式落盘
            encryptedPrefs.edit { putString(ENCRYPTED_KEY_PASSWORD, password) }
        } else {
            cachedSavedUsername = null
            cachedSavedPassword = null
            encryptedPrefs.edit { remove(ENCRYPTED_KEY_PASSWORD) }
        }
        dataStore.edit { prefs ->
            prefs[KEY_REMEMBER_PASSWORD] = rememberPassword
            prefs[KEY_AUTO_LOGIN] = autoLogin
            if (rememberPassword) {
                prefs[KEY_SAVED_USERNAME] = username
            } else {
                prefs.remove(KEY_SAVED_USERNAME)
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

    /** 获取保存的密码（同步，从加密缓存读取） */
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

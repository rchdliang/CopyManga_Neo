package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证数据持久化存储
 * 使用 DataStore<Preferences> 存储 token、用户名、头像和登录偏好
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

    /** 缓存保存的密码 */
    @Volatile
    private var cachedSavedPassword: String? = null

    init {
        runBlocking {
            val prefs = dataStore.data.first()
            cachedToken = prefs[KEY_TOKEN]
            cachedUsername = prefs[KEY_USERNAME]
            cachedAvatar = prefs[KEY_AVATAR]
            cachedRememberPassword = prefs[KEY_REMEMBER_PASSWORD] ?: false
            cachedAutoLogin = prefs[KEY_AUTO_LOGIN] ?: false
            cachedSavedUsername = prefs[KEY_SAVED_USERNAME]
            cachedSavedPassword = prefs[KEY_SAVED_PASSWORD]
        }
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
     * @param password 原始密码
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

    /** 获取保存的密码（Flow） */
    fun getSavedPassword(): Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_SAVED_PASSWORD] }

    /** 获取保存的密码（同步） */
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

    /** 是否已登录（Flow） */
    fun isLoggedIn(): Flow<Boolean> = getToken().map { it != null }

    /** 是否已登录（同步，取缓存值） */
    fun isLoggedInSync(): Boolean = cachedToken != null
}

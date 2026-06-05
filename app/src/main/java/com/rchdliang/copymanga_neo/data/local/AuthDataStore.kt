package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * 使用 DataStore<Preferences> 存储 token 和用户名
 */
@Singleton
class AuthDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_AVATAR = stringPreferencesKey("auth_avatar")
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

    init {
        runBlocking {
            val prefs = dataStore.data.first()
            cachedToken = prefs[KEY_TOKEN]
            cachedUsername = prefs[KEY_USERNAME]
            cachedAvatar = prefs[KEY_AVATAR]
        }
        android.util.Log.d(
            "AuthDataStore",
            "Init complete: token=${if (cachedToken != null) "***" else "null"}"
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
    }

    /** 是否已登录（Flow） */
    fun isLoggedIn(): Flow<Boolean> = getToken().map { it != null }

    /** 是否已登录（同步，取缓存值） */
    fun isLoggedInSync(): Boolean = cachedToken != null
}

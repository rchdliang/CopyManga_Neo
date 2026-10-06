package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Singleton

/** 两种请求模式独立保存会话和登录偏好；旧认证格式直接清空，不迁移。 */
@Singleton
class AuthDataStore(
    private val dataStore: DataStore<Preferences>,
    private val networkSettings: NetworkSettingsStore,
    private val clearWebCookies: () -> Unit = {}
) {
    private data class Session(
        val token: String? = null,
        val username: String? = null,
        val avatar: String? = null,
        val rememberPassword: Boolean = false,
        val autoLogin: Boolean = false,
        val savedUsername: String? = null,
        val savedPassword: String? = null
    )

    private data class ActiveSession(val mode: RequestMode, val session: Session = Session())

    private val lock = Any()
    private val sessions = mutableMapOf<RequestMode, Session>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = MutableStateFlow(ActiveSession(networkSettings.settings.value.requestMode))
    private val revision = MutableStateFlow(0L)
    private val ready = MutableStateFlow(false)
    private var authGeneration = 0L
    private var autoLoginAllowed = true

    val cacheReadyFlow: Flow<Boolean> = ready.asStateFlow()
    val sessionChanges: Flow<Long> = revision.asStateFlow()

    init {
        networkSettings.onChange {
            synchronized(lock) {
                val mode = networkSettings.settings.value.requestMode
                if (mode != active.value.mode) autoLoginAllowed = false
                active.value = ActiveSession(mode, sessions[mode] ?: Session())
                advanceGeneration()
            }
        }
        scope.launch {
            val prefs = dataStore.edit {
                val valid = runCatching {
                    if (it[SCHEMA] != SCHEMA_VERSION) false else {
                        RequestMode.entries.forEach { mode -> readSession(it, mode) }
                        true
                    }
                }.getOrDefault(false)
                if (!valid) {
                    it.clear()
                    it[SCHEMA] = SCHEMA_VERSION
                    clearWebCookies()
                }
            }
            synchronized(lock) {
                RequestMode.entries.forEach { mode -> sessions[mode] = readSession(prefs, mode) }
                publish()
                advanceGeneration()
            }
            ready.value = true
        }
    }

    private fun advanceGeneration() {
        authGeneration++
        revision.value = authGeneration
    }

    private fun publish() {
        val mode = active.value.mode
        active.value = ActiveSession(mode, sessions[mode] ?: Session())
    }

    suspend fun awaitReady() {
        ready.first { it }
    }

    fun isCacheReady(): Boolean = ready.value
    fun getAuthGeneration(): Long = synchronized(lock) { authGeneration }
    fun getRequestModeSync(): RequestMode = active.value.mode

    /** 请求与响应副作用绑定代数，旧请求不能覆盖另一种模式或后来登录的账号。 */
    internal fun ifCurrent(generation: Long, block: () -> Unit) = synchronized(lock) {
        if (authGeneration == generation) block()
    }

    suspend fun saveAuthIfCurrent(
        generation: Long,
        token: String,
        username: String,
        avatar: String?
    ): Boolean {
        awaitReady()
        var saved = false
        dataStore.edit { prefs ->
            synchronized(lock) {
                if (authGeneration == generation) {
                    val mode = active.value.mode
                    val old = sessions[mode] ?: Session()
                    val preferences = if (old.savedUsername == username) old else Session()
                    sessions[mode] =
                        preferences.copy(token = token, username = username, avatar = avatar)
                    autoLoginAllowed = true
                    writeSessions(prefs)
                    publish()
                    advanceGeneration()
                    saved = true
                }
            }
        }
        return saved
    }

    /** 两种登录请求结束后统一保存成功的模式，避免第一个成功结果使第二个请求过期。 */
    internal suspend fun saveLoginSessionsIfCurrent(
        generation: Long,
        loggedIn: List<ImportedSession>,
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean
    ): Boolean {
        require(loggedIn.isNotEmpty() && loggedIn.map { it.mode }.distinct().size == loggedIn.size)
        awaitReady()
        var saved = false
        dataStore.edit { prefs ->
            synchronized(lock) {
                if (authGeneration == generation) {
                    loggedIn.forEach {
                        sessions[it.mode] = Session(
                            token = it.token, username = it.username, avatar = it.avatar,
                            rememberPassword = rememberPassword,
                            autoLogin = rememberPassword && autoLogin,
                            savedUsername = username.takeIf { rememberPassword },
                            savedPassword = password.takeIf { rememberPassword }
                        )
                    }
                    if (loggedIn.any { it.mode == RequestMode.WEB }) clearWebCookies()
                    if (loggedIn.any { it.mode == active.value.mode }) autoLoginAllowed = true
                    writeSessions(prefs)
                    publish()
                    advanceGeneration()
                    saved = true
                }
            }
        }
        return saved
    }

    suspend fun updateMemberInfoIfCurrent(
        generation: Long,
        token: String,
        username: String,
        avatar: String?
    ): Boolean {
        awaitReady()
        var updated = false
        dataStore.edit { prefs ->
            synchronized(lock) {
                val mode = active.value.mode
                val old = sessions[mode] ?: Session()
                if (authGeneration == generation && old.token == token) {
                    sessions[mode] = old.copy(username = username, avatar = avatar)
                    writeSessions(prefs)
                    publish()
                    updated = true
                }
            }
        }
        return updated
    }

    /** 全部模式验证通过后一起写入；仅替换包中包含的模式，不沿用被替换账号的密码。 */
    internal suspend fun saveImportedSessions(
        generation: Long,
        imported: List<ImportedSession>
    ): Boolean {
        awaitReady()
        var saved = false
        dataStore.edit { prefs ->
            synchronized(lock) {
                if (authGeneration == generation) {
                    imported.forEach {
                        sessions[it.mode] = Session(it.token, it.username, it.avatar)
                    }
                    autoLoginAllowed = false
                    if (imported.any { it.mode == RequestMode.WEB }) clearWebCookies()
                    writeSessions(prefs)
                    publish()
                    advanceGeneration()
                    saved = true
                }
            }
        }
        return saved
    }

    suspend fun saveCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        expectedGeneration: Long
    ) {
        awaitReady()
        dataStore.edit { prefs ->
            synchronized(lock) {
                if (authGeneration == expectedGeneration && active.value.session.token != null) {
                    val mode = active.value.mode
                    sessions[mode] = active.value.session.copy(
                        rememberPassword = rememberPassword,
                        autoLogin = rememberPassword && autoLogin,
                        savedUsername = username.takeIf { rememberPassword },
                        savedPassword = password.takeIf { rememberPassword }
                    )
                    writeSessions(prefs)
                    publish()
                }
            }
        }
    }

    /** 仅清除指定模式；手动退出关闭该模式自动登录，另一模式保持原状。 */
    suspend fun clearAuth(
        manualLogout: Boolean = false,
        mode: RequestMode = getRequestModeSync()
    ) {
        awaitReady()
        dataStore.edit { prefs ->
            synchronized(lock) {
                val old = sessions[mode] ?: Session()
                sessions[mode] = old.copy(
                    token = null, username = null, avatar = null,
                    autoLogin = !manualLogout && old.autoLogin
                )
                if (mode == active.value.mode) autoLoginAllowed = !manualLogout
                if (mode == RequestMode.WEB) clearWebCookies()
                writeSessions(prefs)
                publish()
                advanceGeneration()
            }
        }
    }

    /** 401 只失效请求原属的模式，且必须仍是同一代数和 Token。 */
    fun clearAuthSyncIfTokenMatches(
        token: String,
        mode: RequestMode,
        generation: Long
    ): Boolean {
        synchronized(lock) {
            val old = sessions[mode] ?: return false
            if (authGeneration != generation || old.token != token) return false
            sessions[mode] = old.copy(token = null, username = null, avatar = null)
            if (mode == active.value.mode) autoLoginAllowed = true
            if (mode == RequestMode.WEB) clearWebCookies()
            publish()
            advanceGeneration()
        }
        scope.launch {
            dataStore.edit { prefs -> synchronized(lock) { writeSessions(prefs) } }
        }
        return true
    }

    /** 两种模式都存在时一起导出，否则只允许导出当前模式。 */
    internal fun exportSessions(): LoginCredentialBundle = synchronized(lock) {
        val mode = active.value.mode
        fun credential(value: RequestMode): LoginCredential? {
            val session = sessions[value] ?: return null
            val token = session.token?.takeIf { it.isNotBlank() } ?: return null
            val username = session.username?.takeIf { it.isNotBlank() } ?: return null
            return LoginCredential(value, token, username)
        }

        val current = credential(mode) ?: throw IllegalStateException("当前未登录，无法导出登录凭据")
        val all = RequestMode.entries.mapNotNull(::credential)
        LoginCredentialBundle(
            mode,
            if (all.size == RequestMode.entries.size) all else listOf(current)
        )
    }

    fun getToken(): Flow<String?> = active.map { it.session.token }.distinctUntilChanged()
    fun getTokenSync(): String? = active.value.session.token
    fun getTokenForModeSync(mode: RequestMode): String? =
        synchronized(lock) { sessions[mode]?.token }

    fun getUsername(): Flow<String?> = active.map { it.session.username }.distinctUntilChanged()
    fun getUsernameSync(): String? = active.value.session.username
    fun getAvatar(): Flow<String?> = active.map { it.session.avatar }.distinctUntilChanged()
    fun getAvatarSync(): String? = active.value.session.avatar
    fun getRememberPassword(): Flow<Boolean> =
        active.map { it.session.rememberPassword }.distinctUntilChanged()

    fun getRememberPasswordSync(): Boolean = active.value.session.rememberPassword
    fun getAutoLogin(): Flow<Boolean> = active.map { it.session.autoLogin }.distinctUntilChanged()
    fun getAutoLoginSync(): Boolean =
        synchronized(lock) { autoLoginAllowed && active.value.session.autoLogin }

    fun getSavedUsername(): Flow<String?> =
        active.map { it.session.savedUsername }.distinctUntilChanged()

    fun getSavedUsernameSync(): String? = active.value.session.savedUsername
    fun getSavedPassword(): Flow<String?> =
        active.map { it.session.savedPassword }.distinctUntilChanged()

    fun getSavedPasswordSync(): String? = active.value.session.savedPassword
    fun isLoggedIn(): Flow<Boolean> = active.map { it.session.token != null }.distinctUntilChanged()
    fun isLoggedInSync(): Boolean = active.value.session.token != null

    private fun readSession(prefs: Preferences, mode: RequestMode): Session {
        val username = prefs[stringKey(mode, "username")]?.takeIf { it.isNotBlank() }
        val token = prefs[stringKey(mode, "token")]?.takeIf { it.isNotBlank() && username != null }
        val remember = prefs[booleanKey(mode, "remember")] ?: false
        return Session(
            token,
            username.takeIf { token != null },
            prefs[stringKey(mode, "avatar")].takeIf { token != null },
            remember,
            remember && (prefs[booleanKey(mode, "auto")] ?: false),
            prefs[stringKey(mode, "saved_username")].takeIf { remember },
            prefs[stringKey(mode, "saved_password")].takeIf { remember }
        )
    }

    private fun writeSessions(prefs: MutablePreferences) {
        prefs.clear()
        prefs[SCHEMA] = SCHEMA_VERSION
        sessions.forEach { (mode, session) ->
            session.token?.let { prefs[stringKey(mode, "token")] = it }
            session.username?.let { prefs[stringKey(mode, "username")] = it }
            session.avatar?.let { prefs[stringKey(mode, "avatar")] = it }
            prefs[booleanKey(mode, "remember")] = session.rememberPassword
            prefs[booleanKey(mode, "auto")] = session.autoLogin
            session.savedUsername?.let { prefs[stringKey(mode, "saved_username")] = it }
            session.savedPassword?.let { prefs[stringKey(mode, "saved_password")] = it }
        }
    }

    private fun stringKey(mode: RequestMode, field: String) =
        stringPreferencesKey("${mode.name}_$field")

    private fun booleanKey(mode: RequestMode, field: String) =
        booleanPreferencesKey("${mode.name}_$field")

    private companion object {
        val SCHEMA = intPreferencesKey("auth_schema")
        const val SCHEMA_VERSION = 2
    }
}

/** 导入校验后的服务器账号信息，只在持久化前短暂持有。 */
internal data class ImportedSession(
    val mode: RequestMode,
    val token: String,
    val username: String,
    val avatar: String?
)

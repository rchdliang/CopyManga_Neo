package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
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

/** 分模式保存登录偏好；同一账号只保留最近恢复的会话，避免旧模式自动登录互踢。 */
@Singleton
class AuthDataStore(
    private val dataStore: DataStore<Preferences>,
    private val networkSettings: NetworkSettingsStore,
    private val clearWebCookies: () -> Unit = {}
) : com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository {
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

    override val cacheReadyFlow: Flow<Boolean> = ready.asStateFlow()
    override val sessionChanges: Flow<Long> = revision.asStateFlow()

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

    /** 已验证的新会话取代同一账号的另一模式；保留记住的密码供手动登录使用。 */
    private fun invalidateOtherMode(mode: RequestMode, accountNames: Set<String>) {
        RequestMode.entries.filter { it != mode }.forEach { otherMode ->
            val old = sessions[otherMode] ?: return@forEach
            val sameAccount = old.username in accountNames ||
                    (old.username == null && old.savedUsername in accountNames)
            if (sameAccount) {
                sessions[otherMode] = old.copy(
                    token = null, username = null, avatar = null, autoLogin = false
                )
                if (otherMode == RequestMode.WEB) clearWebCookies()
            }
        }
    }

    override suspend fun awaitReady() {
        ready.first { it }
    }

    fun isCacheReady(): Boolean = ready.value
    override fun getReadingSessionSync(): com.rchdliang.copymanga_neo.domain.model.LocalReadingSession? =
        synchronized(lock) {
            if (!ready.value) null else {
                val snapshot = active.value
                com.rchdliang.copymanga_neo.domain.model.LocalReadingSession(
                    snapshot.mode,
                    com.rchdliang.copymanga_neo.domain.model.readingProgressOwner(
                        snapshot.session.token != null,
                        snapshot.session.username
                    ), authGeneration
                )
            }
        }
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
        avatar: String?,
        loginUsername: String? = null
    ): Boolean {
        awaitReady()
        var saved = false
        dataStore.edit { prefs ->
            synchronized(lock) {
                if (authGeneration == generation) {
                    val mode = active.value.mode
                    val old = sessions[mode] ?: Session()
                    val accountNames =
                        setOfNotNull(username, loginUsername).filter { it.isNotBlank() }.toSet()
                    val preferences = if (old.savedUsername in accountNames) old else Session()
                    invalidateOtherMode(mode, accountNames)
                    sessions[mode] =
                        preferences.copy(token = token, username = username, avatar = avatar)
                    if (mode == RequestMode.WEB) clearWebCookies()
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

    /** 仅保存当前模式的已验证会话，同时更新本次登录偏好。 */
    internal suspend fun saveLoginSessionIfCurrent(
        generation: Long,
        loggedIn: ImportedSession,
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        networkGeneration: Long
    ): Boolean {
        awaitReady()
        var saved = false
        dataStore.edit { prefs ->
            networkSettings.ifCurrent(networkGeneration) {
                synchronized(lock) {
                    if (authGeneration == generation && loggedIn.mode == active.value.mode) {
                        invalidateOtherMode(loggedIn.mode, setOf(username, loggedIn.username))
                        sessions[loggedIn.mode] = Session(
                            token = loggedIn.token,
                            username = loggedIn.username,
                            avatar = loggedIn.avatar,
                            rememberPassword = rememberPassword,
                            autoLogin = rememberPassword && autoLogin,
                            savedUsername = username.takeIf { rememberPassword },
                            savedPassword = password.takeIf { rememberPassword }
                        )
                        if (loggedIn.mode == RequestMode.WEB) clearWebCookies()
                        autoLoginAllowed = true
                        writeSessions(prefs)
                        publish()
                        advanceGeneration()
                        saved = true
                    }
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

    /** 只恢复导出模式；可选密码供手动登录使用，不继承本机原账号的密码或自动登录。 */
    internal suspend fun saveImportedSession(
        generation: Long,
        imported: ImportedSession,
        networkGeneration: Long,
        settings: NetworkSettings,
        savedLogin: SavedLoginCredential? = null
    ): Long? {
        require(imported.mode == settings.requestMode)
        awaitReady()
        var savedGeneration: Long? = null
        dataStore.edit { prefs ->
            // 与网络设置变更使用相同的加锁顺序，先保存身份再公布新 Token。
            networkSettings.ifCurrent(networkGeneration) {
                synchronized(lock) {
                    if (authGeneration == generation) {
                        check(networkSettings.saveIfCurrent(networkGeneration, settings)) {
                            "导入期间网络设置已改变"
                        }
                        val accountNames = setOfNotNull(imported.username, savedLogin?.username)
                        invalidateOtherMode(imported.mode, accountNames)
                        sessions[imported.mode] = Session(
                            token = imported.token,
                            username = imported.username,
                            avatar = imported.avatar,
                            rememberPassword = savedLogin != null,
                            autoLogin = false,
                            savedUsername = savedLogin?.username,
                            savedPassword = savedLogin?.password
                        )
                        savedLogin?.let { login ->
                            RequestMode.entries.filter { it != imported.mode }
                                .forEach { otherMode ->
                                    val old = sessions[otherMode] ?: Session()
                                    // 不覆盖另一账号的会话或已记住的密码。
                                    if ((old.username == null || old.username in accountNames) &&
                                        (old.savedUsername == null || old.savedUsername in accountNames)
                                    ) {
                                        sessions[otherMode] = old.copy(
                                            rememberPassword = true,
                                            autoLogin = false,
                                            savedUsername = login.username,
                                            savedPassword = login.password
                                        )
                                    }
                                }
                        }
                        autoLoginAllowed = false
                        if (imported.mode == RequestMode.WEB) clearWebCookies()
                        writeSessions(prefs)
                        publish()
                        advanceGeneration()
                        savedGeneration = authGeneration
                    }
                }
            }
        }
        return savedGeneration
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

    /** 仅导出当前模式；旧模式中的缓存 Token 不应进入迁移包。 */
    internal fun exportSessions(includePassword: Boolean = false): LoginCredentialBundle =
        synchronized(lock) {
            val mode = active.value.mode
            fun credential(value: RequestMode): LoginCredential? {
                val session = sessions[value] ?: return null
                val token = session.token?.takeIf { it.isNotBlank() } ?: return null
                val username = session.username?.takeIf { it.isNotBlank() } ?: return null
                return LoginCredential(value, token, username)
            }

            val current =
                credential(mode) ?: throw IllegalStateException("当前未登录，无法导出登录凭据")
            val savedLogin = if (includePassword) {
                val session = sessions.getValue(mode)
                check(
                    session.rememberPassword && !session.savedUsername.isNullOrBlank() &&
                            !session.savedPassword.isNullOrBlank()
                ) { "当前账号未保存密码，请先登录并勾选记住密码" }
                SavedLoginCredential(
                    requireNotNull(session.savedUsername),
                    requireNotNull(session.savedPassword)
                )
            } else null
            LoginCredentialBundle(mode, listOf(current), savedLogin = savedLogin)
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
    override fun isLoggedInSync(): Boolean = active.value.session.token != null

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

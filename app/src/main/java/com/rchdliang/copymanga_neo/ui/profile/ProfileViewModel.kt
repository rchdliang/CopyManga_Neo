package com.rchdliang.copymanga_neo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 账号页面 UI 状态
 * 注意：密码不再通过 UiState 暴露，改用 [ProfileViewModel.getSavedPasswordSync] 同步读取
 */
data class ProfileUiState(
    val requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode = com.rchdliang.copymanga_neo.domain.model.RequestMode.WEB,
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val isLoginLoading: Boolean = false,
    val loginError: String? = null,
    val showLoginDialog: Boolean = false,
    val showRegisterDialog: Boolean = false,
    val isRegisterLoading: Boolean = false,
    val registerError: String? = null,
    val registeredUsername: String? = null,
    val avatar: String? = null,
    val nickname: String? = null,
    val showProfileEditor: Boolean = false,
    val editableProfile: com.rchdliang.copymanga_neo.domain.model.EditableProfile? = null,
    val isProfileLoading: Boolean = false,
    val isProfileSaving: Boolean = false,
    val profileEditError: String? = null,
    val dayDownloads: Int? = null,
    val dayDownloadsRefresh: String? = null,
    val ticket: Float? = null,
    val rewardTicket: Float? = null,
    val isRefreshing: Boolean = false,
    val savedUsername: String? = null,
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
    val isCredentialLoading: Boolean = false,
    val toastMessage: String? = null,
    val blockedThemes: List<Theme> = emptyList(),
    val isBlacklistEnabled: Boolean = true,
    val isBlacklistLoading: Boolean = true,
    val isBlacklistSaving: Boolean = false,
    val blacklistError: String? = null
)

/** 账号页一次性事件。敏感凭据只通过事件传递，不进入可恢复的 UiState。 */
sealed interface ProfileEvent {
    data class CopyLoginCredential(val credential: String) : ProfileEvent
}

/**
 * 账号页面 ViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val themeBlacklistRepository: ThemeBlacklistRepository,
    private val networkSettings: com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(ProfileUiState(requestMode = networkSettings.settings.value.requestMode))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var memberInfoJob: Job? = null

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ProfileViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoginLoading = false,
                isRegisterLoading = false,
                showRegisterDialog = it.showRegisterDialog && it.registeredUsername == null,
                showLoginDialog = it.showLoginDialog || (it.isRegisterLoading && it.registeredUsername != null),
                loginError = if (it.isRegisterLoading && it.registeredUsername != null)
                    "账号已注册，请尝试登录" else it.loginError,
                isRefreshing = false,
                isCredentialLoading = false,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showProfileEditor() {
        if (!_uiState.value.isLoggedIn || _uiState.value.isProfileSaving) return
        _uiState.update {
            it.copy(
                showProfileEditor = true, isProfileLoading = true,
                editableProfile = null, profileEditError = null
            )
        }
        val generation = userRepository.sessionVersionSync()
        viewModelScope.launch(exceptionHandler) {
            userRepository.getEditableProfile().fold(
                onSuccess = { profile ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    _uiState.update {
                        it.copy(
                            isProfileLoading = false,
                            editableProfile = profile
                        )
                    }
                },
                onFailure = { error ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    _uiState.update {
                        it.copy(
                            isProfileLoading = false,
                            profileEditError = error.message ?: "资料加载失败"
                        )
                    }
                }
            )
        }
    }

    fun hideProfileEditor() {
        if (_uiState.value.isProfileSaving) return
        _uiState.update {
            it.copy(
                showProfileEditor = false,
                editableProfile = null,
                profileEditError = null
            )
        }
    }

    fun saveProfile(nickname: String, avatarJpeg: ByteArray?) {
        if (_uiState.value.isProfileSaving || !_uiState.value.isLoggedIn) return
        _uiState.update { it.copy(isProfileSaving = true, profileEditError = null) }
        val generation = userRepository.sessionVersionSync()
        viewModelScope.launch(exceptionHandler) {
            userRepository.updateProfile(nickname, avatarJpeg).fold(
                onSuccess = { info ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    _uiState.update {
                        it.copy(
                            isProfileSaving = false,
                            showProfileEditor = false,
                            editableProfile = null,
                            nickname = info.nickname,
                            toastMessage = "资料已更新"
                        )
                    }
                },
                onFailure = { error ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    _uiState.update { state ->
                        if (error is com.rchdliang.copymanga_neo.domain.repository.ProfileSavedRefreshException)
                            state.copy(
                                isProfileSaving = false, showProfileEditor = false,
                                editableProfile = null, toastMessage = error.message
                            )
                        else state.copy(
                            isProfileSaving = false,
                            profileEditError = error.message ?: "资料保存失败"
                        )
                    }
                }
            )
        }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            networkSettings.settings.collect { network ->
                _uiState.update { it.copy(requestMode = network.requestMode) }
            }
        }
        viewModelScope.launch(exceptionHandler) {
            combine(
                themeBlacklistRepository.blockedThemes,
                themeBlacklistRepository.isEnabled
            ) { themes, enabled ->
                themes to enabled
            }
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isBlacklistLoading = false,
                            blacklistError = error.message ?: "读取黑名单失败"
                        )
                    }
                }
                .collect { (themes, enabled) ->
                    _uiState.update {
                        it.copy(
                            blockedThemes = themes,
                            isBlacklistEnabled = enabled,
                            isBlacklistLoading = false,
                            blacklistError = null
                        )
                    }
                }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.isAuthCacheReady().first { it }
            userRepository.sessionChanges().collect {
                val loggedIn = userRepository.isLoggedInSync()
                memberInfoJob?.cancel()
                _uiState.update {
                    it.copy(
                        isLoggedIn = loggedIn, nickname = null, dayDownloads = null,
                        dayDownloadsRefresh = null, ticket = null, rewardTicket = null,
                        showProfileEditor = false, editableProfile = null,
                        isProfileLoading = false, isProfileSaving = false, profileEditError = null,
                        isRefreshing = false
                    )
                }
                if (loggedIn) {
                    refreshMemberInfo()
                }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.getUsername().collect { username ->
                _uiState.update { it.copy(username = username) }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.getAvatar().collect { avatar ->
                _uiState.update { it.copy(avatar = avatar) }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.getRememberPassword().collect { remember ->
                _uiState.update { it.copy(rememberPassword = remember) }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.getAutoLogin().collect { autoLogin ->
                _uiState.update { it.copy(autoLogin = autoLogin) }
            }
        }

        viewModelScope.launch(exceptionHandler) {
            userRepository.getSavedUsername().collect { savedUsername ->
                _uiState.update { it.copy(savedUsername = savedUsername) }
            }
        }
        // 密码不再通过 Flow 收集到 UiState，避免明文密码驻留内存状态
    }

    /** 从本机首页黑名单移除题材。 */
    fun removeBlockedTheme(theme: Theme) {
        if (_uiState.value.isBlacklistSaving || _uiState.value.isBlacklistLoading) return
        _uiState.update { it.copy(isBlacklistSaving = true) }
        viewModelScope.launch(exceptionHandler) {
            themeBlacklistRepository.setBlocked(theme, false).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBlacklistSaving = false,
                            toastMessage = "已取消屏蔽「${theme.name}」"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBlacklistSaving = false,
                            toastMessage = error.message ?: "移除失败"
                        )
                    }
                }
            )
        }
    }

    /** 只切换过滤状态，保留标签列表以便再次启用。 */
    fun setBlacklistEnabled(enabled: Boolean) {
        if (_uiState.value.isBlacklistLoading || _uiState.value.isBlacklistSaving) return
        _uiState.update { it.copy(isBlacklistSaving = true) }
        viewModelScope.launch(exceptionHandler) {
            themeBlacklistRepository.setEnabled(enabled).fold(
                onSuccess = { _uiState.update { it.copy(isBlacklistSaving = false) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBlacklistSaving = false,
                            toastMessage = error.message ?: "保存开关失败"
                        )
                    }
                }
            )
        }
    }

    /** 获取保存的密码（同步，供 LoginDialog 预填充使用，不暴露到 UiState） */
    fun getSavedPasswordSync(): String? = userRepository.getSavedPasswordSync()

    /** 刷新用户信息（合并原 refreshMemberInfo 和 refresh 的重复逻辑） */
    fun refreshMemberInfo(isPullToRefresh: Boolean = false) {
        memberInfoJob?.cancel()
        val generation = userRepository.sessionVersionSync()
        memberInfoJob = viewModelScope.launch(exceptionHandler) {
            if (isPullToRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            }
            userRepository.getMemberInfo().fold(
                onSuccess = { info ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    _uiState.update {
                        it.copy(
                            nickname = info.nickname,
                            dayDownloads = info.dayDownloads,
                            dayDownloadsRefresh = formatRefreshTime(info.dayDownloadsRefresh),
                            ticket = info.ticket,
                            rewardTicket = info.rewardTicket,
                            isRefreshing = false
                        )
                    }
                },
                onFailure = { error ->
                    if (userRepository.sessionVersionSync() != generation) return@fold
                    android.util.Log.e(
                        "ProfileViewModel",
                        "Refresh member info failed: ${error.message}"
                    )
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            toastMessage = error.message ?: "刷新失败"
                        )
                    }
                }
            )
        }
    }

    private fun formatRefreshTime(isoString: String?): String? {
        if (isoString == null) return null
        return try {
            val zonedDateTime = ZonedDateTime.parse(isoString)
            val localDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            localDateTime.format(formatter)
        } catch (e: Exception) {
            android.util.Log.e("ProfileViewModel", "Format refresh time failed: ${e.message}")
            isoString
        }
    }

    /** 下拉刷新 */
    fun refresh() {
        refreshMemberInfo(isPullToRefresh = true)
    }

    /** 显示登录对话框 */
    fun showLoginDialog() {
        if (_uiState.value.isRegisterLoading || _uiState.value.isLoginLoading) return
        _uiState.update {
            it.copy(
                showLoginDialog = true,
                showRegisterDialog = false,
                loginError = null
            )
        }
    }

    /** 编辑登录字段后清除上次服务端错误。 */
    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    /** 隐藏登录对话框 */
    fun hideLoginDialog() {
        if (_uiState.value.isLoginLoading) return
        _uiState.update { it.copy(showLoginDialog = false, loginError = null) }
    }

    fun showRegisterDialog() {
        if (_uiState.value.isRegisterLoading || _uiState.value.isLoginLoading || _uiState.value.isLoggedIn) return
        _uiState.update {
            it.copy(
                showLoginDialog = false, showRegisterDialog = true, registerError = null,
                registeredUsername = null
            )
        }
    }

    fun hideRegisterDialog() {
        if (_uiState.value.isRegisterLoading) return
        _uiState.update { it.copy(showRegisterDialog = false, registerError = null) }
    }

    fun clearRegisterError() {
        _uiState.update { it.copy(registerError = null) }
    }

    /** 注册和登录分两步；注册已成功后登录失败，不再回到注册提交。 */
    fun register(username: String, password: String, confirmation: String) {
        if (_uiState.value.isRegisterLoading || _uiState.value.isLoginLoading) return
        val account = username.trim()
        val error = com.rchdliang.copymanga_neo.domain.model.RegistrationPolicy
            .validationError(account, password)
            ?: "两次输入的密码不一致".takeIf { password != confirmation }
        if (error != null) {
            _uiState.update { it.copy(registerError = error) }
            return
        }
        _uiState.update { it.copy(isRegisterLoading = true, registerError = null) }
        viewModelScope.launch(exceptionHandler) {
            userRepository.register(account, password).fold(
                onSuccess = { registration ->
                    _uiState.update { it.copy(registeredUsername = registration.username) }
                    if (!registration.canAutoLogin) {
                        _uiState.update {
                            it.copy(
                                isRegisterLoading = false, showRegisterDialog = false,
                                showLoginDialog = !it.isLoggedIn,
                                toastMessage = "账号已注册，网络或登录状态已变化，请手动登录"
                            )
                        }
                        return@launch
                    }
                    val loginGeneration = userRepository.sessionVersionSync()
                    userRepository.login(account, password, expectedGeneration = loginGeneration)
                        .fold(
                            onSuccess = {
                                // 注册密码不默认持久化，也不沿用旧账号的自动登录设置。
                                userRepository.saveCredentials(
                                    account,
                                    password,
                                    false,
                                    false,
                                    loginGeneration + 1
                                )
                                _uiState.update {
                                    it.copy(
                                        isRegisterLoading = false, showRegisterDialog = false,
                                        registeredUsername = null, toastMessage = "注册并登录成功"
                                    )
                                }
                            },
                            onFailure = { loginError ->
                                _uiState.update {
                                    it.copy(
                                        isRegisterLoading = false, showRegisterDialog = false,
                                        showLoginDialog = true, registeredUsername = account,
                                        loginError = "注册已成功，登录失败：${loginError.message ?: "请重试登录"}",
                                        toastMessage = "账号已注册，请登录，无需再次注册"
                                    )
                                }
                            }
                        )
                },
                onFailure = { registerError ->
                    _uiState.update {
                        it.copy(
                            isRegisterLoading = false,
                            registerError = registerError.message ?: "注册失败"
                        )
                    }
                }
            )
        }
    }

    /** 登录页支持一次输入分别登录当前模式或两种模式。 */
    fun login(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        bothModes: Boolean
    ) {
        if (_uiState.value.isLoginLoading || _uiState.value.isRegisterLoading) return
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(loginError = "账号和密码不能为空") }
            return
        }

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoginLoading = true, loginError = null) }
            userRepository.isAuthCacheReady().first { it }
            val generation = userRepository.sessionVersionSync()
            userRepository.loginWithOptions(
                username, password, bothModes, rememberPassword, autoLogin, generation
            ).fold(
                onSuccess = { result ->
                    if (userRepository.sessionVersionSync() != generation + 1) {
                        _uiState.update {
                            it.copy(
                                isLoginLoading = false,
                                loginError = "登录期间会话或网络设置已改变"
                            )
                        }
                        return@fold
                    }
                    fun label(mode: com.rchdliang.copymanga_neo.domain.model.RequestMode) =
                        if (mode == com.rchdliang.copymanga_neo.domain.model.RequestMode.WEB) "网页模式" else "App 模式"

                    val message = if (result.failures.isEmpty()) null else {
                        result.successfulModes.joinToString(
                            "、",
                            transform = ::label
                        ) + "登录成功；" +
                                result.failures.entries.joinToString("；") { (mode, reason) ->
                                    "${label(mode)}登录失败：$reason"
                                }
                    }
                    _uiState.update {
                        it.copy(
                            isLoginLoading = false,
                            showLoginDialog = result.failures.isNotEmpty(),
                            registeredUsername = null,
                            loginError = message,
                            toastMessage = "网页和 App 模式均登录成功".takeIf {
                                bothModes && result.failures.isEmpty()
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoginLoading = false, loginError = error.message ?: "登录失败")
                    }
                }
            )
        }
    }

    /** 导出当前会话凭据，成功后由界面写入系统剪切板。 */
    fun exportLoginCredential() {
        if (_uiState.value.isCredentialLoading) return

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isCredentialLoading = true) }
            userRepository.exportLoginCredential().fold(
                onSuccess = { credential ->
                    _uiState.update { it.copy(isCredentialLoading = false) }
                    _events.emit(ProfileEvent.CopyLoginCredential(credential))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCredentialLoading = false,
                            isProfileLoading = false,
                            isProfileSaving = false,
                            toastMessage = error.message ?: "导出登录凭据失败"
                        )
                    }
                }
            )
        }
    }

    /** 从剪切板文本验证并导入会话凭据。 */
    fun importLoginCredential(serializedCredential: String) {
        if (_uiState.value.isCredentialLoading) return
        if (serializedCredential.isBlank()) {
            _uiState.update { it.copy(toastMessage = "剪切板中没有登录凭据") }
            return
        }

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isCredentialLoading = true) }
            userRepository.importLoginCredential(serializedCredential).fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isCredentialLoading = false,
                            nickname = info.nickname,
                            dayDownloads = info.dayDownloads,
                            dayDownloadsRefresh = formatRefreshTime(info.dayDownloadsRefresh),
                            ticket = info.ticket,
                            rewardTicket = info.rewardTicket,
                            toastMessage = "登录凭据导入成功"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCredentialLoading = false,
                            toastMessage = error.message ?: "导入登录凭据失败"
                        )
                    }
                }
            )
        }
    }

    /** 登出 */
    fun logout() {
        viewModelScope.launch(exceptionHandler) {
            userRepository.logout().fold(
                onSuccess = {
                    // 服务器退出返回较晚时，不能覆盖期间重新登录的账号界面。
                    if (userRepository.isLoggedInSync()) return@fold
                    _uiState.update {
                        it.copy(
                            isLoggedIn = false,
                            username = null,
                            avatar = null,
                            nickname = null,
                            dayDownloads = null,
                            dayDownloadsRefresh = null,
                            ticket = null,
                            rewardTicket = null
                        )
                    }
                },
                onFailure = { error ->
                    android.util.Log.e("ProfileViewModel", "Logout failed: ${error.message}")
                    if (userRepository.isLoggedInSync()) return@fold
                    _uiState.update {
                        it.copy(
                            isLoggedIn = false,
                            username = null,
                            avatar = null,
                            nickname = null,
                            dayDownloads = null,
                            dayDownloadsRefresh = null,
                            ticket = null,
                            rewardTicket = null,
                            toastMessage = "登出失败"
                        )
                    }
                }
            )
        }
    }
}

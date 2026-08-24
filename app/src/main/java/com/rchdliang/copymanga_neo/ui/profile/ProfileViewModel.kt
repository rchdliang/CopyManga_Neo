package com.rchdliang.copymanga_neo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val isLoginLoading: Boolean = false,
    val loginError: String? = null,
    val showLoginDialog: Boolean = false,
    val avatar: String? = null,
    val nickname: String? = null,
    val dayDownloads: Int? = null,
    val dayDownloadsRefresh: String? = null,
    val ticket: Float? = null,
    val rewardTicket: Float? = null,
    val isRefreshing: Boolean = false,
    val savedUsername: String? = null,
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
    val isCredentialLoading: Boolean = false,
    val toastMessage: String? = null
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ProfileViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoginLoading = false,
                isRefreshing = false,
                isCredentialLoading = false,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            userRepository.isLoggedIn().collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
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

    /** 获取保存的密码（同步，供 LoginDialog 预填充使用，不暴露到 UiState） */
    fun getSavedPasswordSync(): String? = userRepository.getSavedPasswordSync()

    /** 刷新用户信息（合并原 refreshMemberInfo 和 refresh 的重复逻辑） */
    fun refreshMemberInfo(isPullToRefresh: Boolean = false) {
        viewModelScope.launch(exceptionHandler) {
            if (isPullToRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            }
            userRepository.getMemberInfo().fold(
                onSuccess = { info ->
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
        _uiState.update { it.copy(showLoginDialog = true, loginError = null) }
    }

    /** 隐藏登录对话框 */
    fun hideLoginDialog() {
        _uiState.update { it.copy(showLoginDialog = false, loginError = null) }
    }

    /** 登录 */
    fun login(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean
    ) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(loginError = "账号和密码不能为空") }
            return
        }

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoginLoading = true, loginError = null) }
            userRepository.login(username, password).fold(
                onSuccess = {
                    userRepository.saveCredentials(username, password, rememberPassword, autoLogin)
                    _uiState.update {
                        it.copy(
                            isLoginLoading = false,
                            showLoginDialog = false,
                            loginError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoginLoading = false,
                            loginError = error.message ?: "登录失败"
                        )
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

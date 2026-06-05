package com.rchdliang.copymanga_neo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 账号页面 UI 状态
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
    val rewardTicket: Float? = null
)

/**
 * 账号页面 ViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // 监听登录状态
        viewModelScope.launch {
            userRepository.isLoggedIn().collect { loggedIn ->
                _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
                if (loggedIn) {
                    refreshMemberInfo()
                }
            }
        }

        viewModelScope.launch {
            userRepository.getUsername().collect { username ->
                _uiState.value = _uiState.value.copy(username = username)
            }
        }

        viewModelScope.launch {
            userRepository.getAvatar().collect { avatar ->
                _uiState.value = _uiState.value.copy(avatar = avatar)
            }
        }
    }

    /** 刷新用户信息 */
    fun refreshMemberInfo() {
        viewModelScope.launch {
            userRepository.getMemberInfo().fold(
                onSuccess = { info ->
                    _uiState.value = _uiState.value.copy(
                        nickname = info.nickname,
                        dayDownloads = info.dayDownloads,
                        dayDownloadsRefresh = formatRefreshTime(info.dayDownloadsRefresh),
                        ticket = info.ticket,
                        rewardTicket = info.rewardTicket
                    )
                },
                onFailure = { error ->
                    android.util.Log.e(
                        "ProfileViewModel",
                        "Refresh member info failed: ${error.message}"
                    )
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

    /** 显示登录对话框 */
    fun showLoginDialog() {
        _uiState.value = _uiState.value.copy(showLoginDialog = true, loginError = null)
    }

    /** 隐藏登录对话框 */
    fun hideLoginDialog() {
        _uiState.value = _uiState.value.copy(showLoginDialog = false, loginError = null)
    }

    /** 登录 */
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(loginError = "账号和密码不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoginLoading = true, loginError = null)
            userRepository.login(username, password).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoginLoading = false,
                        showLoginDialog = false,
                        loginError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoginLoading = false,
                        loginError = error.message ?: "登录失败"
                    )
                }
            )
        }
    }

    /** 登出 */
    fun logout() {
        viewModelScope.launch {
            userRepository.logout().fold(
                onSuccess = {
                    _uiState.value = ProfileUiState()
                },
                onFailure = { error ->
                    android.util.Log.e("ProfileViewModel", "Logout failed: ${error.message}")
                    _uiState.value = ProfileUiState()
                }
            )
        }
    }
}
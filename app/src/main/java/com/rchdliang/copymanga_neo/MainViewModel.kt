package com.rchdliang.copymanga_neo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = userRepository.isLoggedIn()
        .stateIn(viewModelScope, SharingStarted.Eagerly, userRepository.isLoggedInSync())

    init {
        android.util.Log.d(
            "MainViewModel",
            "isLoggedIn initial: ${userRepository.isLoggedInSync()}"
        )
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            if (userRepository.isLoggedIn().first()) {
                userRepository.getMemberInfo().fold(
                    onSuccess = { /* 静默刷新用户信息 */ },
                    onFailure = { error ->
                        android.util.Log.w(
                            "MainViewModel",
                            "refresh member info failed: ${error.message}"
                        )
                    }
                )
            }
        }
    }
}

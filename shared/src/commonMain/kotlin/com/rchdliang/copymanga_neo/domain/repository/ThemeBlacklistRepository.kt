package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.Theme
import kotlinx.coroutines.flow.Flow

/** 本机首页题材黑名单。 */
interface ThemeBlacklistRepository {
    val blockedThemes: Flow<List<Theme>>
    val isEnabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
    suspend fun setBlocked(theme: Theme, blocked: Boolean): Result<Unit>
}

package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import kotlinx.coroutines.flow.Flow

/** 阅读界面和后台任务只获取无凭据的会话身份，不接触认证存储与 Token。 */
interface ReadingSessionRepository {
    val cacheReadyFlow: Flow<Boolean>
    val sessionChanges: Flow<Long>
    suspend fun awaitReady()
    fun getReadingSessionSync(): LocalReadingSession?
    fun isLoggedInSync(): Boolean
}

package com.rchdliang.copymanga_neo.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class ErrorLogState(val bytes: Long = 0, val error: String? = null)

/** 错误日志独立于网络诊断和用户备份，默认记录，仅由用户主动导出。 */
interface ErrorLogRepository {
    val state: StateFlow<ErrorLogState>
    suspend fun refresh()
    suspend fun clear(): Result<Unit>
    suspend fun export(): Result<ByteArray>
}

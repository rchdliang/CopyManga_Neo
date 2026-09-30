package com.rchdliang.copymanga_neo.domain.repository

import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream

data class NetworkLogState(
    val enabled: Boolean = false,
    val bytes: Long = 0,
    val error: String? = null
)

/** 本机网络诊断记录；导出仅由用户主动发起。 */
interface NetworkLogRepository {
    val state: StateFlow<NetworkLogState>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun clear(): Result<Unit>
    suspend fun export(output: OutputStream): Result<Unit>
}

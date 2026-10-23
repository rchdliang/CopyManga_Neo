package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NetworkProbe
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import kotlinx.coroutines.flow.StateFlow

interface NetworkSettingsRepository {
    val supportedRequestModes: List<com.rchdliang.copymanga_neo.domain.model.RequestMode>
        get() = com.rchdliang.copymanga_neo.domain.model.RequestMode.entries
    val officialAppDefaults: com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
    val settings: StateFlow<NetworkSettings>
    fun generatePseudoId(): String
    fun generateUmString(): String
    suspend fun diagnoseOfficialApp(): Result<String>
    suspend fun save(settings: NetworkSettings): Result<Unit>
    suspend fun reset(): Result<Unit>
    suspend fun previousSettings(): Result<NetworkSettings> = Result.failure(IllegalStateException("没有可载入的上次配置"))
    suspend fun rollback(): Result<Unit>
    suspend fun discover(): Result<Unit>
    suspend fun test(settings: NetworkSettings): Result<List<NetworkProbe>>
    suspend fun refreshIfNeeded()
}

package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ComicExportDraft
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportRequest
import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import kotlinx.coroutines.flow.StateFlow

interface ComicExportRepository {
    val state: StateFlow<ComicExportState>
    val messages: kotlinx.coroutines.flow.Flow<String>
    suspend fun prepare(request: ComicExportRequest): Result<ComicExportPlan>
    suspend fun saveDraft(
        plan: ComicExportPlan,
        outputs: List<ComicExportOutput> = emptyList()
    ): Result<String>

    suspend fun readDraft(id: String): Result<ComicExportDraft?>
    suspend fun start(
        plan: ComicExportPlan, format: ComicExportFormat, destination: String, directory: Boolean,
        previousOutputs: List<ComicExportOutput> = emptyList()
    ): Result<Unit>

    suspend fun awaitRestoration(): Result<Unit>
    fun cancel()
    suspend fun dismiss(): Result<Unit>

    /** 只根据输入描述剩余输出的保存位置，不执行文件操作。 */
    fun describeLocation(
        plan: ComicExportPlan,
        format: ComicExportFormat,
        previousOutputs: List<ComicExportOutput> = emptyList()
    ): ComicExportLocation?
}

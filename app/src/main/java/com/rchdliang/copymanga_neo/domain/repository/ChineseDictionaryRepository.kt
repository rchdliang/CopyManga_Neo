package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.text.TraditionalChineseConverter
import kotlinx.coroutines.flow.StateFlow

data class DictionaryUpdate(val version: String, val updated: Boolean)

/** 本地读取不联网；安装缺失词典或主动更新时查询 OpenCC 最新稳定发布。 */
interface ChineseDictionaryRepository {
    val converter: StateFlow<TraditionalChineseConverter?>
    val version: StateFlow<String?>
    val isWorking: StateFlow<Boolean>
    suspend fun load(): Result<Unit>
    suspend fun install(): Result<Unit>
    suspend fun update(): Result<DictionaryUpdate>
}

package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Debug 样本仅驻留内存，供前台展示后台请求发现的插图行号。 */
data class NovelImagePositionDebugSample(val id: String, val json: String)

@Singleton
class NovelImagePositionDebugStore @Inject constructor() {
    private val pending = MutableStateFlow<List<NovelImagePositionDebugSample>>(emptyList())
    val samples = pending.asStateFlow()

    /** 同一响应尚未确认时只排队一次；不同分卷的样本逐个展示。 */
    fun offer(sample: NovelImagePositionDebugSample) {
        pending.update { samples ->
            if (samples.any { it.id == sample.id }) samples else samples + sample
        }
    }

    fun dismiss(id: String) {
        pending.update { samples -> samples.filterNot { it.id == id } }
    }
}

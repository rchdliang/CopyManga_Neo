package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ComicCardMetadata
import kotlinx.coroutines.flow.StateFlow

/** 所有漫画列表共用详情补全，不因黑名单开关停止预加载。 */
interface ComicMetadataRepository {
    val metadata: StateFlow<Map<String, ComicCardMetadata>>
    val failures: StateFlow<Map<String, String>>
    val cacheScope: StateFlow<String>
    fun preload(paths: List<String>, priority: Boolean = false)
    fun retry(path: String)
}

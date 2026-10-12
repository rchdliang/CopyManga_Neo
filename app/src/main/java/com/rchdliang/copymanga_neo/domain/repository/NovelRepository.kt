package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelCacheProgress
import com.rchdliang.copymanga_neo.domain.model.NovelCacheUsage
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.NovelPage
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.flow.StateFlow

/** 小说浏览和阅读能力；界面通过此契约访问数据，不依赖网络及缓存实现。 */
interface NovelRepository {
    val cachedVolumes: StateFlow<List<NovelCachedVolume>>
    suspend fun discover(
        mode: RequestMode,
        owner: String,
        offset: Int,
        order: String,
        theme: String,
        author: String
    ): Result<NovelPage>

    suspend fun search(
        mode: RequestMode,
        owner: String,
        offset: Int,
        query: String,
        type: String
    ): Result<NovelPage>

    suspend fun tags(mode: RequestMode, owner: String): Result<List<NovelTag>>
    suspend fun detail(mode: RequestMode, owner: String, path: String): Result<NovelBook>
    suspend fun volumes(mode: RequestMode, owner: String, path: String): Result<List<NovelVolume>>
    suspend fun readVolume(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String,
        allImages: Boolean = false,
        refresh: Boolean = false,
        onProgress: (NovelCacheProgress) -> Unit = {},
        forDownload: Boolean = false
    ): Result<NovelReadingVolume>

    suspend fun removeVolume(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String
    ): Result<Unit>

    suspend fun removeBook(mode: RequestMode, owner: String, path: String): Result<Unit>
    suspend fun refreshCachedVolumes(): Result<Unit>
    suspend fun cacheUsage(): Result<NovelCacheUsage>
    suspend fun clearCache(): Result<Unit>
}

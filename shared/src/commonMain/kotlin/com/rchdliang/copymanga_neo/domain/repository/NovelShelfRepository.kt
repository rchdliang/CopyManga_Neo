package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NovelShelfPage

/** 小说云端书架；收藏状态来自服务端，按当前账号及请求模式隔离。 */
interface NovelShelfRepository {
    suspend fun list(offset: Int = 0): Result<NovelShelfPage>
    suspend fun contains(path: String): Result<Boolean>
    suspend fun add(bookId: String, path: String): Result<Unit>
    suspend fun remove(bookId: String, path: String): Result<Unit>
    suspend fun removeAll(collectIds: List<Int>): Result<Unit>
}

package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.StorageUsage

interface StorageRepository {
    suspend fun getUsage(): Result<StorageUsage>
    suspend fun clearImageCache(): Result<Unit>
    suspend fun clearNovelCache(): Result<Unit>
}

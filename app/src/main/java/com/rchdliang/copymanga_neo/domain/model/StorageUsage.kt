package com.rchdliang.copymanga_neo.domain.model

data class StorageUsage(
    val imageCacheBytes: Long,
    val downloadBytes: Long,
    val availableBytes: Long
)

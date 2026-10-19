package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 仅存公开漫画资料，不持久化详情响应中的账号状态。 */
@Entity(tableName = "comic_card_cache")
data class ComicCardCacheEntity(@PrimaryKey val cacheKey: String, val metadataJson: String)
